; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts.processor.skipper
  (:require
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.trials.helper :refer [get-script-by-script-key get-scripts-atoms]]
    [cider-ci.ex.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.map :as map :refer [deep-merge convert-to-array]]
    [cider-ci.ex.scripts.processor.starter :refer [amend-with-start-when-defaults start-when-fulfilled?]]
    [clj-commons-exec :as commons-exec]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))

(def this-ns *ns*)

(defn- debug-pipe [msg x]
  (logging/debug this-ns msg x)
  x)

(defn- dependency-finished? [params script-atom trial]
  (boolean
    (when-let [depend-on-script (get-script-by-script-key (:script params) trial)]
               (finished? depend-on-script))))

(defn- unsatisfiable? [& args]
  (not (apply start-when-fulfilled? args)))

(defn- any-unsatisfiable? [script-atom trial]
  (boolean
    (when-let [start-when-conditions (:start-when @script-atom)]
      (->> start-when-conditions
           (debug-pipe ['any-unsatisfiable? 'start-when-conditions])
           convert-to-array
           (debug-pipe ['any-unsatisfiable? 'array])
           (map amend-with-start-when-defaults)
           (debug-pipe ['any-unsatisfiable? 'with-defaults])
           (filter #(dependency-finished? % script-atom trial))
           (debug-pipe ['any-unsatisfiable? 'dependency-finished])
           (filter #(unsatisfiable? % script-atom trial))
           (debug-pipe ['any-unsatisfiable? 'not-fulfilled?])
           empty?
           not))))

(defn- unsatisfiable-scripts [trial]
  (->> (get-scripts-atoms trial)
       (debug-pipe ['unsatisfiable-scripts 'script-atoms])
       (filter #(-> % deref pending?))
       (debug-pipe ['unsatisfiable-scripts 'pending-scripts-atoms])
       (filter #(any-unsatisfiable? % trial))
       (debug-pipe ['unsatisfiable-scripts 'result])
       doall))

(defn- skip-script [script-atom]
  (swap! script-atom
         (fn [script]
           (if (= "pending" (:state script))
             (assoc script
                    :state "skipped"
                    :skipped_at (time/now)
                    :skipped_by "unsatisfiable check")
             script))))

(defn  skip-scripts [trial]
  (->> (unsatisfiable-scripts trial)
       (map skip-script)
       doall)
  trial)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
