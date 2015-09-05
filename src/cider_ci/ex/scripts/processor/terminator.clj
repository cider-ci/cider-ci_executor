; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts.processor.terminator
  (:require
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.trials.helper :as trials]
    [cider-ci.ex.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.map :as map :refer [deep-merge convert-to-array]]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))


;### set terminate flag #######################################################

(defn set-terminate-flag [script-atom]
  (logging/debug 'set-terminate-flag {:script-atom script-atom})
  (swap! script-atom  #(assoc % :terminate true )))

;### terminate when conditions fullfiled ######################################

(defn- terminate-when-fulfilled? [params script-atom trial]
  (catcher/wrap-with-log-error
    (let [script-key (:script params)
          script (trials/get-script-by-script-key script-key trial)
          state (:state script)]
      (some #{state} (:states params)))))

(defn- terminate-when-all-fulfilled? [script-atom trial]
  (catcher/wrap-with-log-error
    (boolean
      (when-let [terminators (not-empty (:terminate-when @script-atom))]
        (every?  #(terminate-when-fulfilled? % script-atom trial)
                (-> terminators convert-to-array))))))

(defn- log-seq [msg seq]
  (logging/debug msg {:seq seq})
  (doall seq))

(defn set-to-terminate-when-fulfilled [trial]
  (catcher/wrap-with-log-error
    (->> (trials/get-scripts-atoms trial)
         ;(log-seq 'script-atoms)
         (filter #(-> % deref executing?))
         ;(log-seq 'script-atoms-executing)
         (filter #(terminate-when-all-fulfilled? % trial))
         ;(log-seq 'script-atoms-fulfilled)
         (map set-terminate-flag)
         doall)))


;### abort ####################################################################

(defn- set-to-terminate-when-executing [trial]
  (->> (trials/get-scripts-atoms trial)
       (filter #(-> % deref executing?))
       (map set-terminate-flag)
       doall))

(defn abort [trial] (set-to-terminate-when-executing trial))

;### Debug ####################################################################
(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
