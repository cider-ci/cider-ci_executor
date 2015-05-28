; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.ex.scripts.processor
  (:require 
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.scripts.processor.skipper :refer [skip-scripts]]
    [cider-ci.ex.scripts.processor.starter :refer [start-scripts]]
    [cider-ci.ex.scripts.processor.terminator :refer [set-terminate-scripts]]
    [cider-ci.ex.scripts.processor.trigger :as trigger]
    [cider-ci.ex.trial.helper :as trial]
    [cider-ci.ex.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.map :as map :refer [deep-merge convert-to-array]]
    [clj-commons-exec :as commons-exec]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))





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



;###############################################################################

(defn process [trial]
  (try 
    (trigger/add-watchers trial)
    (trigger/trigger trial "initial")
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
    trial
    (finally 
      (catcher/wrap-with-suppress-and-log-warn (trigger/remove-watchers trial))
      (catcher/wrap-with-suppress-and-log-warn (set-skipped-state-if-not-finnished trial)))))

(defn abort [trial-id]
  ; TODO 
  )

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
