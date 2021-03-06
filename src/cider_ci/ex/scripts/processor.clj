; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts.processor
  (:require
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.scripts.processor.patch :as patch]
    [cider-ci.ex.scripts.processor.skipper :refer [skip-script]]
    [cider-ci.ex.scripts.processor.starter :refer [start-scripts]]
    [cider-ci.ex.scripts.processor.trigger :as trigger]
    [cider-ci.ex.trials.helper :as trials]
    [cider-ci.ex.utils.state :refer [pending? executing-or-waiting? executing? finished?]]
    [cider-ci.utils.core :refer :all]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

;###############################################################################

(defn- set-skipped-state-if-not-finnished [trial]
  (doseq [script-atom (trials/get-scripts-atoms trial)]
    (when-not (finished? script-atom)
      (skip-script script-atom "leftover check")))
  trial)

(defn- finished-less-then-x-ago? [item x]
  (boolean (when-let [finished-at (:finished_at item)]
             (time/before? (time/minus (time/now) x)
                           finished-at))))

;###############################################################################

(defn process [trial]
  (try
    (trigger/add-watchers trial)
    (patch/add-watchers trial)
    (trigger/trigger trial "initial")
    (loop []
      (Thread/sleep 100)
      (let [scripts-atoms (trials/get-scripts-atoms trial)]
        (when (and (not (every? finished? scripts-atoms))
                   (or (some executing-or-waiting? scripts-atoms)
                       ; give the triggers a chance to evaluate:
                       (not-empty (->> scripts-atoms
                                       (filter finished?)
                                       (filter #(finished-less-then-x-ago?
                                                  @% (time/seconds 1)))))))
          (recur))))
    trial
    (finally
      (catcher/snatch {}
        (trigger/remove-watchers trial))
      (catcher/snatch {}
        (set-skipped-state-if-not-finnished trial))
      (catcher/snatch {}
        (future (Thread/sleep (* 60 1000))
                (patch/remove-watchers trial))))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
