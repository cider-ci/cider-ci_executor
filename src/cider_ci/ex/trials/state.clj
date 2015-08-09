; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.trials.state
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.ex.attachments :as attachments]
    [cider-ci.ex.git :as git]
    [cider-ci.ex.port-provider :as port-provider]
    [cider-ci.ex.reporter :as reporter]
    [cider-ci.ex.result :as result]
    [cider-ci.ex.scripts.processor]
    [cider-ci.ex.trials.helper :refer :all]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.http :refer [build-server-url]]
    [cider-ci.utils.map :refer [deep-merge]]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.pprint :as pprint]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [robert.hooke :as hooke]
    ))


;#### manage trials ###########################################################
(defonce ^:private trials-atom (atom {}))

(defn get-trials []
  "Retrieves the received and not yet discarded trials"
  (flatten (map (fn [t] [(:params-atom (second t))])
       (seq @trials-atom))))

(defn get-trial [id]
  "Returns the entity stored in the trials-atom if it exists or nil otherwise.
  The properties of the trial can be retrived (-> trial :params-atom deref)
  or more conveniently via get-trial-properties."
  (@trials-atom id))

(defn get-trial-properties [id]
  "Returns the current properties of the trial or nil if it is not found.
  Use get-trial to access all attributes."
  (when-let [trial (get-trial id)]
    (-> trial :params-atom deref)))

(defn create-trial
  "Creates a new trial, stores it in trials under its id and returns the trial"
  [params]
  (let [id (:trial_id params)
        params (assoc params :started_at (time/now))]
    (swap! trials-atom
           (fn [trials params id]
             (conj trials {id {:params-atom (atom  params)
                               :report-agent (agent [] :error-mode :continue)}}))
           params id)
    (@trials-atom id)))


