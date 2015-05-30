; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.ex.scripts.processor.starter
  (:require 
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.trial.helper :as trial]
    [cider-ci.ex.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.map :as map :refer [deep-merge convert-to-array]]
    [clj-commons-exec :as commons-exec]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))


(defn start-when-fulfilled? [params script-a trial]
  (logging/info 'start-when-fulfilled? [params script-a trial])
  (catcher/wrap-with-log-error
    (let [script-key (:script params)
          script (trial/get-script-by-script-key script-key trial)
          state (:state script)]
      (logging/debug "start-when-fulfilled?" {:script-key script-key :state state :params params :script script})
      (some #{state} (:states params)))))

(defn amend-with-start-when-defaults [properties]
  (deep-merge {:type "script"
               :states ["passed"]}
              properties))

(defn- start-when-all-fulfilled? [script-a trial]
  (catcher/wrap-with-log-error
    (every?  
      #(start-when-fulfilled? % script-a trial) 
      (->> @script-a 
          :start-when 
          convert-to-array
          (map amend-with-start-when-defaults)))))

(defn- scripts-atoms-to-be-started [trial]
  (catcher/wrap-with-log-error
    (->> (trial/get-scripts-atoms trial)
         (filter #(-> % deref pending? )) 
         (filter #(start-when-all-fulfilled? % trial)))))

(defn- exec?
  "Determines if the script should be executed from this thread/scope. 
  Sets the state to dispatched and returns true if so. 
  Returns false otherwise. "
  [script-atom] 
  (let [r (str (.getId (Thread/currentThread)) "_" (rand))
        swap-in-fun (fn [script]
                      (if (= (:state script) "pending"); we will exec if it is still pending
                        (assoc script :state "waiting" :dispatch_thread_sig r)
                        script))]
    (swap! script-atom swap-in-fun)
    (= r (:dispatch_thread_sig @script-atom))))


;####################################################################

(defonce exclusive-resource-agents-atom (atom {}))

(defn create-execlusive-resource-agent [agent-name]
  (agent {:resource agent-name}
         :error-mode :continue))

(defn get-exclusive-resource-agent [agent-name]
  (-> (swap! exclusive-resource-agents-atom
             (fn [exclusive-resource-agents agent-name]
               (if (get exclusive-resource-agents agent-name)
                 exclusive-resource-agents
                 (assoc exclusive-resource-agents 
                        agent-name (create-execlusive-resource-agent agent-name))))
             agent-name)
      (get agent-name)))


(defn exec-inside-agent [agent-state script-atom]
  (when (= "waiting" (:state @script-atom))
    (exec/execute script-atom))
  (assoc agent-state 
         :last_finished_at (time/now)
         :last_script @script-atom))

(defn dispatch [script-atom]
  (if-let [exclusive-resource (:exclusive-executor-resource @script-atom)]
    (send-off (get-exclusive-resource-agent exclusive-resource) 
              exec-inside-agent script-atom)
    (future (exec/execute script-atom))))

;####################################################################

(defn start-scripts [trial]
  (->> (scripts-atoms-to-be-started trial)
       (filter exec?) ; avoid potential race condition here
       (map dispatch)
       doall)
  trial)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
