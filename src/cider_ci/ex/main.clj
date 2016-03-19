; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;
(ns cider-ci.ex.main
  (:gen-class)
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.self]
    [cider-ci.ex.accepted-repositories :as accepted-repositories]
    [cider-ci.ex.reporter :as reporter]
    [cider-ci.ex.sync :as sync]
    [cider-ci.ex.traits :as traits]
    [cider-ci.ex.trials :as trials]
    [cider-ci.ex.trials.state :as trials.state]
    [cider-ci.ex.trials.working-dir-sweeper]
    [cider-ci.ex.web :as web]
    [cider-ci.utils.config :as config :refer [get-config merge-into-conf]]
    [cider-ci.utils.fs :as fs]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.self :as self]


    [clojure.tools.logging :as logging]
    [clj-commons-exec :as commons-exec]
    [camel-snake-kebab.core :refer [->kebab-case]]

    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown]
    [me.raynes.fs :as clj-fs]
    ))

(defn initialize-and-configure-tmp-dir []
  (when-not (:tmp_dir (get-config))
    (merge-into-conf {:tmp_dir
                      (str (-> "java.io.tmpdir"
                               System/getProperty
                               clj-fs/absolute
                               clj-fs/normalized)
                           (File/separator)
                           "cider-ci")}))
  (-> (get-config) :tmp_dir File. .mkdir))


(defn hostname []
  ( -> (commons-exec/sh ["hostname"])
       deref :out clojure.string/trim))


(defn initialize-and-configure-dir [dir & {:keys [clean] :or {clean false}}]
  (let [dirstr (name dir)
        dirkw (keyword dir)
        dirname (->kebab-case dirstr)]
    (when-not (get (get-config) dirkw)
      (merge-into-conf {dirkw (str (-> (get-config) :tmp_dir)
                                   File/separator
                                   dirname)}))
    (let [dir  (-> (get-config) (get dirkw))]
      (when clean (clj-fs/delete-dir dir))
      (clj-fs/mkdir dir))))

(defn basic-auth []
  (or (-> (get-config) :basic_auth)
      (throw (IllegalStateException. ":basic_auth config missing"))))

(defn initialize []
  (initialize-and-configure-tmp-dir)
  (initialize-and-configure-dir :repositories_dir)
  (initialize-and-configure-dir :working_dir :clean true))

(defn log-env []
  (logging/info 'current-user (System/getProperty "user.name"))
  (doseq [[k v] (System/getenv)]
    (logging/debug 'env-var k v)))

(defn initialize-config []
  (config/initialize
    {:defaults {:name (hostname)}
     :filenames [(fs/system-path "." "config" "config.yml")]
     :overrides {:service :executor
                 :hostname (hostname)}})

  (when-not (-> (get-config) :basic_auth :username)
    (config/merge-into-conf
      {:basic_auth
       {:username (-> (get-config) :name str)}}))

  (when-not (-> (get-config) :basic_auth :password)
    (when-let [secret (-> (get-config) :server_secret)]
      (config/merge-into-conf
        {:basic_auth
         {:password (cider-ci.auth.http-basic/create-password
                      (-> (get-config) :basic_auth :username str)
                      secret)}}))))


(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [_] (System/exit -1))}
    (logging/info self/application-str)
    (log-env)
    (logbug.thrown/reset-ns-filter-regex #".*cider.ci.*")
    (logging/info "starting -main " args)
    (initialize-config)
    (initialize)
    (traits/initialize)
    (accepted-repositories/initialize)
    (nrepl/initialize (-> (get-config) :nrepl ))
    (web/initialize {:basic_auth (basic-auth) :http (-> (get-config) :http)})
    (sync/initialize)
    (trials.state/initialize)
    (cider-ci.ex.trials.working-dir-sweeper/initialize)))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
