; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts.processor.trigger
  (:require
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.scripts.processor.terminator :refer [set-to-terminate-when-fulfilled]]
    [cider-ci.ex.scripts.processor.starter :refer [start-scripts]]
    [cider-ci.ex.scripts.processor.skipper :refer [skip-unsatisfiable-scripts]]
    [cider-ci.ex.trials.helper :as trials]
    [cider-ci.ex.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.map :as map :refer [deep-merge convert-to-array]]
    [clj-commons-exec :as commons-exec]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))


(defn trigger [trial msg]
  (catcher/wrap-with-log-error (skip-unsatisfiable-scripts trial))
  (catcher/wrap-with-log-error (set-to-terminate-when-fulfilled trial))
  (catcher/wrap-with-log-error (start-scripts trial))
  trial)

(defn- eval-watch-trigger [trial old-state new-state]
  (future
    (catcher/wrap-with-log-error
      (when (not= (:state old-state) (:state new-state))
        (trigger trial (str (:name old-state) " : "
                            (:state old-state) " -> " (:state new-state)))))))


(defn add-watchers [trial]
  (doseq [script-atom (trials/get-scripts-atoms trial)]
    (add-watch script-atom
               :trigger
               (fn [_ script-atom old-state new-state]
                 (eval-watch-trigger trial old-state new-state)))))

(defn remove-watchers [trial]
  (doseq [script-atom (trials/get-scripts-atoms trial)]
    (remove-watch script-atom :trigger)))

