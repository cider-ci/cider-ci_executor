; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;
(ns cider-ci.ex.main
  (:gen-class)
  (:import
    [java.io File]
    )
  (:require
    [camel-snake-kebab.core :refer :all]
    [cider-ci.ex.accepted-repositories :as accepted-repositories]
    [cider-ci.ex.reporter :as reporter]
    [cider-ci.ex.sync :as sync]
    [cider-ci.ex.traits :as traits]
    [cider-ci.ex.trials :as trials]
    [cider-ci.ex.trials.state :as trials.state]
    [cider-ci.ex.trials.working-dir-sweeper]
    [cider-ci.ex.web :as web]
    [cider-ci.utils.config :as config :refer [get-config merge-in-config]]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.nrepl :as nrepl]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown]
    [me.raynes.fs :as clj-fs]
    ))

(defn initialize-and-configure-tmp-dir []
  (when-not (:tmp_dir (get-config))
    (merge-in-config {:tmp_dir
                      (str (-> "java.io.tmpdir"
                               System/getProperty
                               clj-fs/absolute
                               clj-fs/normalized)
                           (File/separator)
                           "cider-ci")}))
  (-> (get-config) :tmp_dir File. .mkdir))

(defn initialize-and-configure-dir [dir & {:keys [clean] :or {clean false}}]
  (let [dirstr (name dir)
        dirkw (keyword dir)
        dirname (->kebab-case dirstr)]
    (when-not (get (get-config) dirkw)
      (merge-in-config {dirkw (str (-> (get-config) :tmp_dir)
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
    (logging/info 'env-var k v)
    ))

(defn -main [& args]
  (catcher/wrap-with-log-error
    (log-env)
    (drtom.logbug.thrown/reset-ns-filter-regex #".*cider.ci.*")
    (logging/info "starting -main " args)
    (config/initialize)
    (initialize)
    (traits/initialize)
    (accepted-repositories/initialize)
    (nrepl/initialize (-> (get-config) :nrepl ))
    (http/initialize {:basic_auth (-> (get-config) :basic_auth)})
    (web/initialize {:basic_auth (basic-auth) :http (-> (get-config) :http)})
    (reporter/initialize (:reporter (get-config)))
    (sync/initialize)
    (trials.state/initialize)
    (cider-ci.ex.trials.working-dir-sweeper/initialize)
    ))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
