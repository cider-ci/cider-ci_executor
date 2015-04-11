; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.ex.scripts.processor
  (:require 
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.scripts.helper :as scripts]
    [cider-ci.ex.trial.helper :as trial]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.map :as map]
    [cider-ci.utils.with :as with]
    [clj-commons-exec :as commons-exec]
    [clojure.tools.logging :as logging]
    ))


(defn- dependency-fulfilled? [dependency script-a trial]
  (with/log-error
    (let [name (:name dependency)
          script (trial/get-script-by-name name trial)
          state (:state script)]
      (logging/info "dependency-fulfilled?" {:name name :state state :dependency dependency :script script})
      (some #{state} (:states dependency)))))

(defn- dependencies-fulfilled? [script-a trial]
  (with/log-error
    (every?  
      #(dependency-fulfilled? % script-a trial) 
      (-> @script-a :dependencies map/convert-to-array))))

(defn- scripts-atoms-ready-for-execution [trial]
  (with/log-error
    (->> (trial/get-scripts-atoms trial)
         (filter #(-> % deref scripts/pending? )) 
         (filter #(dependencies-fulfilled? % trial)))))

(defn- exec?
  "Determines if the script should be executed from this thread/scope. 
  Sets the state to dispatched and returns true if so. 
  Returns false otherwise. "
  [script-atom] 
  (let [r (str (.getId (Thread/currentThread)) "_" (rand))
        swap-in-fun (fn [script]
                      (if (= (:state script) "pending"); we will exec if it is still pending
                        (assoc script :state "executing" :dispatch_thread_sig r)
                        script))]
    (swap! script-atom swap-in-fun)
    (= r (:dispatch_thread_sig @script-atom))))
 
(defn- trigger 
  ([trial]
   (trigger trial "unknown origin"))
  ([trial msg]
   (logging/info "start trigger: " msg)
   (with/log-error
     (->> (scripts-atoms-ready-for-execution trial)
          (filter exec?) ; avoid potential race condition here
          (map #(future (exec/execute %)
                        (trigger trial (str msg " >> post " (:name (deref %)) " trigger"))))
          doall
          (map deref)
          doall))
   (logging/info "done trigger: " msg) 
   trial))

;### public fns ###############################################################

(defn set-skipped-state-if-not-finnished [trial]
  (doseq [script-a (trial/get-scripts-atoms trial)]
    (when-not (scripts/finished? @script-a)
      (swap! script-a #(assoc % :state "skipped"))))
  trial)

(defn process [trial]
  (-> trial 
      (trigger "initial")
      set-skipped-state-if-not-finnished))

(defn abort [trial-id]
  ; TODO 
  )




;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
