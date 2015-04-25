; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
 
(ns cider-ci.ex.trial.helper
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.catcher :as catcher]
    ))

(def ^:private terminal-states #{"passed" "failed" "aborted" "skipped"})

(defn get-params-atom [trial]
  (catcher/wrap-with-log-error (:params-atom trial)))

(defn get-id [trial]
  (catcher/wrap-with-log-error (:trial_id @(get-params-atom trial))))

(defn get-working-dir [trial]
  (catcher/wrap-with-log-error (:working_dir @(get-params-atom trial))))

(defn get-scripts-atoms [trial]
  (catcher/wrap-with-log-error (:scripts @(get-params-atom trial))))

(defn get-script-by-name [script-name trial]
  (catcher/wrap-with-log-error 
    (->> (get-scripts-atoms trial)
         (map deref)
         (filter #(= script-name (:name %)))
         first )))


(defn scripts-done? [trial]
  (catcher/wrap-with-log-error (->> trial 
                       get-scripts-atoms 
                       (map deref)
                       (map :state)
                       (every? terminal-states))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
