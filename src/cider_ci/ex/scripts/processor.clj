; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.ex.scripts.processor
  (:require 
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.scripts.processor.terminator :refer [set-terminate-scripts]]
    [cider-ci.ex.scripts.processor.starter :refer [start-scripts]]
    [cider-ci.ex.scripts.processor.skipper :refer [skip-scripts]]
    [cider-ci.ex.trial.helper :as trial]
    [cider-ci.ex.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.map :as map :refer [deep-merge convert-to-array]]
    [clj-commons-exec :as commons-exec]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))




(defn- trigger [trial msg]
  (logging/info "TRIGGER: " msg)
  (catcher/wrap-with-log-error (skip-scripts trial))
  (catcher/wrap-with-log-error (set-terminate-scripts trial))
  (catcher/wrap-with-log-error (start-scripts trial)) 
  trial)

(defn- eval-watch-trigger [trial old-state new-state]
  (future 
    (catcher/wrap-with-log-error
      (when (not= (:state old-state) (:state new-state))
        (trigger trial (str (:name old-state) " : " 
                            (:state old-state) " -> " (:state new-state)))))))


;###############################################################################

(defn- touch [trial]
  ; touch a file in the working dir every minute during execution so the
  ; working dir will not be removed prematurely by the sweeper 
  (when (= 0  (-> (System/currentTimeMillis) (/ 1000) double int (mod 60))) 
    (commons-exec/sh ["touch" "._cider-ci.executing"]
                     {:dir (-> trial :params-atom deref :working_dir)})))

;###############################################################################

(defn- set-skipped-state-if-not-finnished [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (when-not (finished? script-atom)
      (swap! script-atom #(assoc % :state "skipped"))))
  trial)

(defn- finished-less-then-x-ago? [item x]
  (boolean (when-let [finished-at (:finished_at item)]
             (time/before? (time/minus (time/now) x)
                           finished-at))))


;### watchers #################################################################

(defn- add-watchers [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (add-watch script-atom 
               :trigger 
               (fn [_ script-atom old-state new-state]
                 (eval-watch-trigger trial old-state new-state)))))

(defn- remove-watchers [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (remove-watch script-atom :trigger)))


;###############################################################################


(defn process [trial]
  (try 
    (add-watchers trial)
    (trigger trial "initial")
    (loop []
      (Thread/sleep 100)
      (touch trial)
      (let [scripts-atoms (trial/get-scripts-atoms trial)]
        (when (and (not (every? finished? scripts-atoms))
                   (or (some executing? scripts-atoms)
                       ; give the triggers a chance to evaluate: 
                       (not-empty (->> scripts-atoms 
                                       (filter finished?)
                                       (filter #(finished-less-then-x-ago? 
                                                  @% (time/seconds 1)))))))
          (recur))))
    (remove-watchers trial)
    trial
    (finally 
      (set-skipped-state-if-not-finnished trial))))

(defn abort [trial-id]
  ; TODO 
  )

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
