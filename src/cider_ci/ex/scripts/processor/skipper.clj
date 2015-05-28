; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.ex.scripts.processor.skipper
  (:require 
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.trial.helper :as trial :refer [get-script-by-script-key]]
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

(defn- log-seq [msg seq]
  (logging/debug this-ns msg {:seq seq})
  (doall seq))

(defn- dependency-finished? [params script-atom trial]
  (boolean 
    (when-let [depend-on-script (get-script-by-script-key (:script-key params) trial)]
               (finished? depend-on-script))))

(defn- start-when-not-fulfilled? [& args]
  (not (apply start-when-fulfilled? args)))

(defn- any-unsatisfiable? [script-atom trial]
  (boolean 
    (when-let [start-when-conditions (:start-when @script-atom)]
      (->> start-when-conditions
           convert-to-array
           amend-with-start-when-defaults
           (filter #(dependency-finished? % script-atom trial))
           (filter #(start-when-not-fulfilled? % script-atom trial))
           empty? 
           not))))

(defn- unsatisfiable-scripts [trial]
  (->> (trial/get-scripts-atoms trial)
       (log-seq 'script-atoms)
       (filter #(-> % deref pending?))
       (log-seq 'pending-scripts-atoms)
       (filter #(any-unsatisfiable? % trial))
       (log-seq 'unsatisfiable-scripts-atoms)
       doall))

(defn  skip-scripts [trial]
  (->> (unsatisfiable-scripts trial)
       (map #(swap! % (fn [script]
                        (if (= "pending" (:state script))
                          (assoc script :state "skipped")
                          script))))
       doall)
  trial)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
