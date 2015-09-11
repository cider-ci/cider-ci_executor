; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts.processor.patch
  (:require
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.trials.helper :as trial]
    [cider-ci.ex.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.map :as map :refer [deep-merge convert-to-array]]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))

(defn- eval-patch [trial old-state new-state]
  (catcher/wrap-with-suppress-and-log-error
    (trial/send-patch-via-agent trial {:scripts {(:key new-state) new-state}})
    ))

(defn add-watchers [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (add-watch script-atom
               :patch
               (fn [_ script-atom old-state new-state]
                 (eval-patch trial old-state new-state)))))

(defn remove-watchers [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (remove-watch script-atom :patch)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'eval-patch)
