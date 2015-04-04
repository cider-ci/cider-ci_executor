; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
 
(ns cider-ci.ex.trial
  (:import 
    [java.io File ]
    )
  (:require
    [cider-ci.ex.attachments :as attachments]
    [cider-ci.ex.exec :as exec]
    [cider-ci.ex.git :as git]
    [cider-ci.ex.port-provider :as port-provider]
    [cider-ci.ex.reporter :as reporter]
    [cider-ci.ex.result :as result]
    [cider-ci.ex.script :as script]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.exception :as exception]
    [cider-ci.utils.http :refer [build-server-url]]
    [cider-ci.utils.with :as with]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.pprint :as pprint]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [robert.hooke :as hooke]
    )
  (:use 
    [clojure.algo.generic.functor :only [fmap]]
    ))


;#### sending updates #########################################################
(defn create-update-sender-via-agent [report-agent]
  (fn [params]
    (logging/debug "invoke anonymous update-sender" [params])
    (with/logging
      (let [url (build-server-url (:patch_path params))
            fun (fn [agent-state]
                  (with/logging
                    (let [res (reporter/patch-as-json-with-retries url params)]
                      (conj agent-state params))))]
        (logging/debug "sending report off" {:url url})
        (send-off report-agent fun)))))

(defn send-trial-patch 
  "Sends just the patch-params" 
  [report-agent params patch-params]
  ((create-update-sender-via-agent report-agent) 
   (conj (select-keys params [:patch_path])
         patch-params)))


;#### prepare trial ###########################################################
(defn- set-and-send-start-params [params-atom report-agent]
  (swap! params-atom (fn [params] (conj params {:state "executing"}))) 
  (send-trial-patch report-agent @params-atom (select-keys @params-atom [:state :started_at])))

(defn- prepare-and-insert-scripts [params-atom]
  (let [initial-scripts (:scripts @params-atom)
        script-atoms (->> initial-scripts
                          (into [])
                          (sort-by #(or (-> % second :order) 0) )
                          (map (fn [[script-name script-spec]]
                                 [script-name (atom (conj script-spec
                                                          {:name script-name}
                                                          (select-keys @params-atom
                                                                       [:environment_variables 
                                                                        :job_id 
                                                                        :trial_id 
                                                                        :working_dir])))]))
                          (into {}))]
    (swap! params-atom #(conj %1 {:scripts %2}) script-atoms)
    (map (fn [[k v]] v) script-atoms)))

;#### manage trials ###########################################################
(defonce ^:private trials-atom (atom {}))
;(clojure.pprint/pprint trials-atom)
(defn get-trials [] 
  "Retrieves the received and not yet discarded trials"
  (flatten (map (fn [t] [(:params-atom (second t))])
       (seq @trials-atom))))

(defn get-trial [id]
  (when-let [trial (@trials-atom id)]
    (:params-atom trial)))

(defn- create-trial   
  "Creates a new trial, stores it in trials under it's id and returns the trial"
  [params]
  (let [id (:trial_id params)]
    (swap! trials-atom 
           (fn [trials params id]
             (conj trials {id {:params-atom (atom  params)
                               :report-agent (agent [] :error-mode :continue)}}))
           params id)
    (@trials-atom id)))

(defn- delete-trial [id trial]
  (when (= 0 (:exit @(commons-exec/sh ["rm" "-rf" (:working_dir trial)])))
    (swap! trials-atom #(dissoc %1 %2) id)))


;#### execute #################################################################
(defn execute [params] 
  (logging/info execute [params])
  (let [started-at (time/now)
        trial (create-trial (conj params {:started_at started-at}))
        report-agent (:report-agent trial)
        params-atom (:params-atom trial)]
    (try 
      (let [
            working-dir (git/prepare-and-create-working-dir params)
            _ (swap! params-atom #(conj %1 {:working_dir %2}) working-dir) 
            scripts-atoms (prepare-and-insert-scripts  params-atom)
            ports (into {} (map (fn [[port-name port-params]] 
                                  [port-name (port-provider/occupy-port 
                                               (or (:inet_address port-params) "localhost") 
                                               (:min port-params) 
                                               (:max port-params))])
                                (:ports @params-atom)))]
        (try 

          (set-and-send-start-params params-atom report-agent)

          ; inject the ports into the scripts
          (doseq [script-atom scripts-atoms]
            (swap! script-atom #(conj %1 {:ports %2}) ports))

          (script/process scripts-atoms  nil)

          (attachments/put working-dir 
                           (:trial_attachments @params-atom) 
                           (build-server-url (:trial_attachments_path @params-atom)))

          (attachments/put working-dir 
                           (:tree_attachments @params-atom) 
                           (build-server-url (:tree_attachments_path @params-atom)))

          (let [final-state (if (every? (fn [script-atom] 
                                          (= "passed" (:state @script-atom))) 
                                        scripts-atoms) 
                              "passed" 
                              "failed")]

            (swap! params-atom 
                   #(conj %1 {:state %2, :finished_at (time/now)}) 
                   final-state)

            (result/try-read-and-merge working-dir params-atom)

            ((create-update-sender-via-agent report-agent) @params-atom))

          (finally 
            (doseq [[_ port] ports]
              (port-provider/release-port port))
            trial)))

      (catch Exception e
        (let [e-str (exception/stringify e)]
          (swap! params-atom (fn [params] 
                               (conj params 
                                     {:state "failed", 
                                      :finished_at (time/now)
                                      :error e-str })))
          (logging/error e-str)
          ((create-update-sender-via-agent report-agent) @params-atom)))
      (finally trial))
    trial))


;#### trials cleaner service ##################################################
(defn clean-all-trails []
  (doseq [[id trial] @trials-atom] 
    (with/suppress-and-log-warn
      (delete-trial id trial))))

(defn clean-outdated-trials []
  (doseq [[id trial] @trials-atom] 
    (with/suppress-and-log-warn
      (let [params @(:params-atom trial) 
            timestamp (or (:finished_at params) (:started_at params))]
        (when (> (time/in-minutes (time/interval timestamp (time/now))) 150)
          (delete-trial id trial))))))

(daemon/define "trial-sweeper" 
  start-trial-sweeper 
  stop-trial-sweeper 
  1
  (logging/debug "trial-sweeper")
  (clean-outdated-trials))

;#### initialize ###############################################################
(defn initialize []
  (.addShutdownHook 
    (Runtime/getRuntime)
    (Thread. (fn [] 
               (stop-trial-sweeper)
               (clean-all-trails))))
  (start-trial-sweeper))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)




