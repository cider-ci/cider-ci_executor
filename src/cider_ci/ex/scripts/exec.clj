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
    [cider-ci.ex.scripts.exec.shared :refer :all]
    [cider-ci.ex.scripts.exec.terminator :refer [terminate create-watchdog pid-file-path]]
    [cider-ci.ex.environment-variables :as environment-variables]
    [cider-ci.ex.scripts.script :as script]
    [cider-ci.ex.shared :refer :all]
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

;### script & wrapper file ####################################################

(def wrapper-extension
  (cond SystemUtils/IS_OS_WINDOWS ".fsx"
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

(defn- sudo-env-vars [vars]
  (->> vars
       (map (fn [[k v]]
              (str k "=" v )))))

;### wrapper ##################################################################

(defn- wrapper-body [params]
  (cond
    SystemUtils/IS_OS_UNIX (script/render "wrapper.sh"
                                          {:pid-file-path (pid-file-path params)
                                           :script-file-path (script-file-path params)
                                           :working-dir-path (working-dir params)
                                           })
    SystemUtils/IS_OS_WINDOWS (script/render "wrapper.fsx"
                                             {:pid-file-path (pid-file-path params)
                                              :script-file-path (script-file-path params)
                                              :working-dir-path (working-dir params)
                                              :exec-user-name (exec-user-name)
                                              :environment-variables (:environment-variables params)
                                              })))

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

(defn- wrapper-command [params]
  (cond
    SystemUtils/IS_OS_UNIX (concat ["sudo" "-u" (exec-user-name)]
                                   (-> params :environment-variables sudo-env-vars)
                                   ["-n" "-i" (:wrapper-file params)])
    SystemUtils/IS_OS_WINDOWS [(-> (get-config) :windows :fsi_path)
                               (:wrapper-file params)]))

(defn exec-sh [params]
  (let [command (wrapper-command params)
        watchdog (:watchdog params)]
    (commons-exec/sh
      command
      {:watchdog watchdog})))

(defn- get-final-parameters [exec-res]
  {:finished_at (time/now)
   :exit_status (:exit exec-res)
   :state (condp = (:exit exec-res)
            0 "passed"
            "failed")
   :stdout (:out exec-res)
   :stderr (:err exec-res)
   :error (:error exec-res)})

(defn- set-script-atom-for-execption [script-atom e]
  (let [e-str (thrown/stringify e)]
    (logging/warn e-str)
    (add-error script-atom e-str)
    (swap! script-atom
           (fn [params]
             (conj params
                   {:state "failed"
                    :finished_at (time/now)})))))

(defn- wait-for-or-terminate [script-atom]
  (let [exec-future (:exec-future @script-atom)
        timeout (:timeout @script-atom)
        started-at (:started_at @script-atom)]
    (while (not (realized? exec-future))
      (when (expired? script-atom)
        (add-error script-atom "Timeout reached!")
        (terminate script-atom))
      (when (:terminate @script-atom)
        (add-error script-atom "Termination requested!")
        (terminate script-atom))
      (when (expired? script-atom (time/seconds 60))
        (throw (IllegalStateException. (str "Giving up to wait for termination!" @script-atom))))
      (Thread/sleep 1000))))

(defn execute [script-atom]
  (try (merge-params script-atom
                     {:started_at (time/now)
                      :state "executing"
                      :watchdog (create-watchdog)
                      :environment-variables (environment-variables/prepare @script-atom)})
       ; must be done again/twice/separately because the environment-variables must be
       ; set properly when calling create-wrapper-file and create-wrapper-file
       (merge-params script-atom
                     {:script-file (create-script-file @script-atom)
                      :wrapper-file (create-wrapper-file @script-atom)})
       (let [exec-future (exec-sh @script-atom)]
         (merge-params script-atom {:exec-future exec-future})
         (wait-for-or-terminate script-atom)
         (merge-params script-atom (get-final-parameters @exec-future)))
       script-atom
       (catch Exception e
         (set-script-atom-for-execption script-atom e)
         script-atom)))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(remove-ns (symbol (str *ns*)))
;(debug/debug-ns *ns*)
