; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.exec
  (:import 
    [java.io File]
    [java.util UUID]
    [org.apache.commons.exec ExecuteWatchdog]
    )
  (:require
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.system :as system]
    [cider-ci.utils.with :as with]
    [cider-ci.utils.exception :as exception]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    ))

(def conf (atom {:environment_variables {}}))

;### Helpers ##################################################################
(defn rubyize-keys [some-hash]
  (into {} (map (fn [p] [(keyword (string/replace (name (first p)) #"-" "_")) (second p)] ) some-hash)))

(defn upper-case-keys [some-hash]
  (into {} (map (fn [p] [ (string/upper-case (name (first p))) (second p)] ) some-hash)))


;### Execute ##################################################################
(defn- get-current-user-name []
  (System/getProperty "user.name"))

(defn- get-exec-user []
  (or 
    (-> @conf (:sudo) (:user))
    (get-current-user-name)))
  
(defn- sudo-env [vars]
  (->> vars
       (map (fn [[k v]]
              (str k "=" v )))))

(defn- prepare-script-file [script]
  (let [script-file (File/createTempFile "cider-ci_", ".script")]
    (.deleteOnExit script-file)
    (spit script-file script)
    (.setExecutable script-file true false)
    script-file))

(defn- commons-exec-sh [command env-variables timeout]
  (commons-exec/sh 
    command 
    {:env (conj {} (System/getenv) env-variables)
     :watchdog (* 1000 timeout)}))

(defn- set-params-for-execption [params e]
  (let [e-str (exception/stringify e)]
    (logging/warn e-str)
    (conj params
          {:state "failed"
           :error e-str })))

(defn- determine-final-parameters [exec-res]
  {:finished_at (time/now)
   :exit_status (:exit exec-res)
   :state (condp = (:exit exec-res) 
            0 "success" 
            "failed")
   :stdout (:out exec-res)
   :stderr (:err exec-res) 
   :error (:error exec-res)
   })

(defn- working-dir [params]
  (.getAbsolutePath (File. (:working_dir params))))

(defn- prepare-env-variables [params]
  (upper-case-keys 
    (rubyize-keys
      (conj { }
            {:cider-ci_working_dir working-dir} 
            (or (:ports params) {}) 
            (or (:environment_variables params) {})
            (or (:environment_variables (:exec @conf)) {})))))


;### exec shell ###############################################################
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

(defn exec-future [params timeout-or-watchdog]
  (let [env-variables (prepare-env-variables params)
        script-file (prepare-script-file (:body params))  
        command (command script-file (working-dir params) env-variables)
        _ (logging/debug {:command command})]
    (commons-exec-sh command env-variables timeout-or-watchdog)))


;### Exec script ##############################################################
(defn exec-script-for-params [params]
  (try
    (let [started {:started_at (time/now)}
          timeout (or (:timeout params) 200)
          exec-res (deref (exec-future params timeout))]
      (conj params started (determine-final-parameters exec-res)))
    (catch Exception e
      (set-params-for-execption params e))))

; TODO debug timeout & watchdog
; (exec-script-for-params {:execution_id "b8a70909-8e33-4128-bfd9-f37a9b33f5d7", :interpreter nil, :trial_id "0ccbd36e-ede3-419c-b2cc-669b697ba9e8", :name "main", :type "main", :working_dir "./tmp", :ports {}, :id "8c8f3922-7ad1-40b6-bb5a-a8c35ea53bca", :order 5, :timeout 1, :environment_variables {:cider_ci_trial_id "0ccbd36e-ede3-419c-b2cc-669b697ba9e8", :cider_ci_task_id "893a6c93-5642-49bf-bde0-f1c5dce2b368", :cider_ci_execution_id "b8a70909-8e33-4128-bfd9-f37a9b33f5d7", :the_answer 42}, :body "sleep 5 && echo 'done'"})

;### Service TODO test ########################################################
(defn start-service-process [params]
  (with/log-debug-result
    (try
      (let [started {:started_at (time/now)}
            timeout (or (:timeout params) 200)
            watchdog (ExecuteWatchdog. timeout) 
            exec-promise (exec-future params watchdog)]
        (conj params started {:watchdog watchdog
                              :exec_promise exec-promise }))
      (catch Exception e
        (set-params-for-execption params e)))))

(defn stop-service [params]
  (.destroyProcess (:watchdog params))
  (let [exec-res (deref (:exec_promise params))]
    (conj params 
          (determine-final-parameters exec-res)
          {:status "success"})))


;### Initialize ###############################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
; (debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


