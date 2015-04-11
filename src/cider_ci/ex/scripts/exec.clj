; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts.exec
  (:import 
    [org.apache.commons.exec ExecuteWatchdog]
    [java.io File]
    )

  (:require 
    [cider-ci.utils.config :as config :refer [get-config]]
    [clojure.string :as string]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [cider-ci.utils.debug :as debug]
    [clojure.tools.logging :as logging]
    [clj-time.core :as time]
    [cider-ci.utils.exception :as exception]

    ))

; TODO us clj-fs or our fs ..
(defn- working-dir [params]
  (.getAbsolutePath (File. (:working_dir params))))

; TODO: 
; * remove (or ... ) not needed with conj
(defn- prepare-env-variables [params]
  (->> (conj { }
             {:CIDER_CI_WORKING_DIR (working-dir params)} 
             (or (:ports params) {}) 
             (or (:environment_variables params) {}))
       (filter (fn [[k v]] (not= nil v))) 
       (map (fn [[k v]] [(name k) (str v)]))
       (map (fn [[k v]] [(string/upper-case k) v])) ; TODO,  remove this with Cider-CI version 3.0.0
       (into {})))


; TODO use doto ...
(defn- prepare-script-file [script]
  (let [script-file (File/createTempFile "cider-ci_", ".script")]
    (.deleteOnExit script-file)
    (spit script-file script)
    (.setExecutable script-file true false)
    script-file))

(defn- get-current-user-name []
  (System/getProperty "user.name"))

(defn- get-exec-user []
  (or (-> (get-config) :sudo :user)
      (get-current-user-name)))

(defn- sudo-env [vars]
  (->> vars
       (map (fn [[k v]]
              (str k "=" v )))))

(defn- interpreter [env_vars]
  (flatten ["sudo" "-u" (get-exec-user) env_vars  "-n" "-i"]))

(defn- create-command-wrapper-file [working-dir script-file]
  (let [command-wrapper-file (File/createTempFile "cider-ci_", ".command_wrapper")
        script (str "cd '" working-dir "' && " (.getAbsolutePath script-file)) ]
    (.deleteOnExit command-wrapper-file)
    (spit command-wrapper-file script)
    (.setExecutable command-wrapper-file true false)
    (.getAbsolutePath command-wrapper-file)))


(defn- command [script-file working-dir env-variables]
  (flatten (concat (interpreter (sudo-env env-variables)) 
                   [(create-command-wrapper-file working-dir script-file)])))


(defn- commons-exec-sh [command env-variables watchdog]
  (commons-exec/sh 
    command 
    {:env (conj {} (System/getenv) env-variables)
     :watchdog watchdog}))


(defn exec-future [params timeout-or-watchdog]
  (let [env-variables (prepare-env-variables params)
        script-file (prepare-script-file (:body params))  
        command (command script-file (working-dir params) env-variables)]
    (logging/debug {:command command})
    (commons-exec-sh command env-variables timeout-or-watchdog)))


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
  (let [e-str (exception/stringify e)]
    (logging/warn e-str)
    (swap! script-atom
           (fn [params e-str]
             (conj params
                   {:state "failed"
                    :error e-str }))
           e-str ))) 

(def ^:private debug-recent-execs (atom '()))
(defn- debug-recent-execs-push [exec]
  (swap! debug-recent-execs (fn [re e] (conj (take 5 re ) e)) exec))

(defn execute [script-atom]
  (debug-recent-execs-push script-atom)
  (try (let [params @script-atom
             timeout (or (:timeout params) 200)
             watchdog (ExecuteWatchdog. (* 1000 timeout))]
         (swap! script-atom 
                (fn [params watchdog]
                  (assoc params
                         :started_at (time/now)
                         :state "executing"
                         :watchdog watchdog))
                watchdog)
         (let [exec-res @(exec-future params watchdog)]
           (swap! script-atom
                  (fn [params res]
                    (conj params res))
                  (get-final-parameters exec-res))))
       (catch Exception e
         (set-script-atom-for-execption script-atom e))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
