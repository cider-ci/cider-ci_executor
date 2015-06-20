; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
(ns cider-ci.ex.scripts.exec
  (:import
    [org.apache.commons.exec ExecuteWatchdog]
    [java.io File]
    )
  (:require
    [cider-ci.ex.scripts.exec.terminator :refer [terminate create-watchdog preper-terminate-script-prefix]]
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
             (or (:environment-variables params) {}))
       (filter (fn [[k v]] (not= nil v)))
       (map (fn [[k v]] [(name k) (str v)]))
       (map (fn [[k v]] [(string/upper-case k) v])) ; TODO,  remove this with Cider-CI version 3.0.0
       (into {})))


(defn- prepare-script-file [params]
  (let [script (:body params)
        script-file (File/createTempFile "cider-ci_" ".script")]
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


;##############################################################################

(defn- create-command-wrapper-file [params]
  (let [working-dir (working-dir params)
        wrapper-file (doto (File/createTempFile "cider-ci_", ".command_wrapper")
                       .deleteOnExit
                       (.setExecutable true false)
                       (spit  (str (preper-terminate-script-prefix params)
                                   "cd '" working-dir "' "
                                   "&& " (.getAbsolutePath (:script-file params)))))]
    (logging/debug {:WRAPPER-FILE (.getAbsolutePath wrapper-file)})
    (.getAbsolutePath wrapper-file)))

(defn- command [params]
  (flatten (concat (interpreter (sudo-env (:environment-variables params)))
                   [(create-command-wrapper-file params)])))

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
                         :script-file (prepare-script-file params)
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
;(debug/debug-ns *ns*)
