; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
(ns cider-ci.ex.scripts.exec
  (:import
    [java.io File]
    [org.apache.commons.exec ExecuteWatchdog]
    [org.apache.commons.lang3 SystemUtils]
    )
  (:require
    [cider-ci.ex.scripts.script :as script]
    [cider-ci.ex.scripts.exec.terminator :refer [terminate create-watchdog pid-file-path]]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.fs :as ci-fs]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.set :refer [difference union]]
    [clojure.string :as string :refer [split trim]]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [me.raynes.fs :as clj-fs]
    ))

;##############################################################################

(defn- script-extension [params]
  (cond
    (:extension params) (:extension params)
    SystemUtils/IS_OS_WINDOWS ".bat"
    :else ""))

(defn- working-dir [params]
  (.getAbsolutePath (File. (:working_dir params))))

(defn- prepare-env-variables [params]
  (->> (merge { }
              {:CIDER_CI_WORKING_DIR (working-dir params)}
              (:ports params)
              (:environment-variables params))
       (filter (fn [[k v]] (not= nil v)))
       (map (fn [[k v]] [(name k) (str v)]))
       (map (fn [[k v]] [(string/upper-case k) v]))
       (into {})))


;### script & wrapper file ####################################################

(def wrapper-extension
  (cond SystemUtils/IS_OS_WINDOWS ".ps1"
        SystemUtils/IS_OS_UNIX ""))

(defn- script-file-path [params]
  (str (:private_dir params) File/separator
       (ci-fs/path-proof (:name params))
       "_script" (script-extension params)))

(defn- create-script-file [params]
  (let [script (:body params)
        script-file (clj-fs/file (script-file-path params))]
    (doto script-file clj-fs/create .deleteOnExit (spit script)
      (.setExecutable true false))
    (.getAbsolutePath script-file)))


;### user and sudo ############################################################

(defn- get-current-user-name []
  (System/getProperty "user.name"))

(defn- exec-user-name []
  (or (-> (get-config) :exec-user :name)
      (get-current-user-name)))

(defn- sudo-env-vars [vars]
  (->> vars
       (map (fn [[k v]]
              (str k "=" v )))
       ))

;### wrapper ##################################################################

(defn- wrapper-body [params]
  (cond
    SystemUtils/IS_OS_UNIX (script/render "wrapper.sh"
                                          {:pid-file-path (pid-file-path params)
                                           :script-file-path (script-file-path params)
                                           :working-dir-path (working-dir params)
                                           })
    SystemUtils/IS_OS_WINDOWS (script/render "wrapper.ps1"
                                             {:script-file-path (script-file-path params)
                                              :working-dir-path (working-dir params)})))

(defn- wrapper-file-path [params]
  (str (:private_dir params) File/separator
       (ci-fs/path-proof (:name params))
       "_wrapper" wrapper-extension ))

(defn- create-wrapper-file [params]
  (let [working-dir (working-dir params)
        wrapper-file (doto (clj-fs/file (wrapper-file-path params))
                       clj-fs/create
                       .deleteOnExit
                       (spit (wrapper-body params))
                       (.setExecutable true false))]
    (logging/debug {:WRAPPER-FILE (.getAbsolutePath wrapper-file)})
    (.getAbsolutePath wrapper-file)))


;##############################################################################

(defn- command [params]
  (cond
    SystemUtils/IS_OS_UNIX (concat ["sudo" "-u" (exec-user-name)]
                                   (-> params prepare-env-variables sudo-env-vars)
                                   ["-n" "-i" (:wrapper-file params)])
    SystemUtils/IS_OS_WINDOWS ["PowerShell.exe" "-ExecutionPolicy" "ByPass" "-File" (:wrapper-file params)]))

(defn- commons-exec-sh [command env-variables watchdog]
  (commons-exec/sh
    command
    {:env (conj {} (System/getenv) env-variables)
     :watchdog watchdog }))

(defn exec-sh [params]
  (let [command (command params)]
    (commons-exec-sh command
                     (:environment-variables params)
                     (:watchdog params))))

(defn- get-final-parameters [exec-res]
  {:finished_at (time/now)
   :exit_status (:exit exec-res)
   :state (condp = (:exit exec-res)
            0 "passed"
            "failed")
   :stdout (:out exec-res)
   :stderr (:err exec-res)
   :error (:error exec-res)
   })

(defn- set-script-atom-for-execption [script-atom e]
  (let [e-str (thrown/stringify e)]
    (logging/warn e-str)
    (swap! script-atom
           (fn [params e-str]
             (conj params
                   {:state "failed"
                    :finished_at (time/now)
                    :error e-str }))
           e-str )))

(def ^:private debug-recent-execs (atom '()))

(defn- debug-recent-execs-push [exec]
  (swap! debug-recent-execs (fn [re e] (conj (take 5 re ) e)) exec))

(defn execute [script-atom]
  (debug-recent-execs-push script-atom)
  (try (let [timeout (or (:timeout @script-atom) 200)
             started-at (time/now)
             watchdog (create-watchdog)]
         (logging/debug "TIMEOUT" timeout)
         (swap! script-atom
                (fn [params watchdog started-at]
                  (conj params
                        {:started_at started-at
                         :state "executing"
                         :watchdog watchdog
                         :environment-variables (prepare-env-variables params)
                         :script-file (create-script-file params)
                         :wrapper-file (create-wrapper-file params)
                         }))
                watchdog started-at)
         (let [exec-future (exec-sh @script-atom)]
           (loop []
             (logging/debug 'eval-termination @script-atom)
             (when
               (and (not (realized? exec-future))
                    (or
                      (:terminate @script-atom)
                      (time/after? (time/now)
                                   (time/plus started-at (time/seconds timeout)))))
               (terminate exec-future script-atom))
             (when-not (realized? exec-future)
               (Thread/sleep 100)
               (recur)))
           (swap! script-atom
                  (fn [params res]
                    (conj params res))
                  (get-final-parameters @exec-future))))
       (catch Exception e
         (set-script-atom-for-execption script-atom e))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(remove-ns (symbol (str *ns*)))
(debug/debug-ns *ns*)
