; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.ex.scripts.processor
  (:require 
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.trial.helper :as trial]
    [cider-ci.ex.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.map :as map]
    [clj-commons-exec :as commons-exec]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))


;##############################################################################

(defn- dependency-fulfilled? [dependency script-a trial]
  (catcher/wrap-with-log-error
    (let [name (:name dependency)
          script (trial/get-script-by-name name trial)
          state (:state script)]
      (logging/info "dependency-fulfilled?" {:name name :state state :dependency dependency :script script})
      (some #{state} (:states dependency)))))

(defn- dependencies-fulfilled? [script-a trial]
  (catcher/wrap-with-log-error
    (every?  
      #(dependency-fulfilled? % script-a trial) 
      (-> @script-a :dependencies map/convert-to-array))))

(defn- scripts-atoms-ready-for-execution [trial]
  (catcher/wrap-with-log-error
    (->> (trial/get-scripts-atoms trial)
         (filter #(-> % deref pending? )) 
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


;###############################################################################

(defn terminator-fulfilled? [terminator script-atom trial]
  (catcher/wrap-with-log-error
    (let [name (:name terminator)
          script (trial/get-script-by-name name trial)
          state (:state script)]
      (logging/info "terminator-fulfilled?" {:name name :state state :terminator terminator :script script})
      (some #{state} (:states terminator)))))

(defn terminators-fulfilled [script-atom trial]
  (catcher/wrap-with-log-error
    (boolean 
      (when-let [terminators (not-empty (:terminators @script-atom))]
        (logging/debug 'terminators-fulfilled {:terminators terminators})
        (every?  #(terminator-fulfilled? % script-atom trial) 
                (-> terminators map/convert-to-array))))))


(defn log-seq [msg seq]
  (logging/debug msg {:seq seq})
  (doall seq))

(defn- set-terminate-scripts  [trial]
  (catcher/wrap-with-log-error
    (->> (trial/get-scripts-atoms trial)
         (log-seq 'script-atoms)
         (filter #(-> % deref executing?))
         (log-seq 'script-atoms-executing)
         (filter #(terminators-fulfilled % trial))
         (log-seq 'script-atoms-fulfilled)
         (map (fn [script-atom]
                (swap! script-atom  #(assoc % :terminate true )))
              ))))


;###############################################################################

(defn exec-ready-scripts [trial]
  (->> (scripts-atoms-ready-for-execution trial)
       (filter exec?) ; avoid potential race condition here
       (map #(future (exec/execute %)))
       doall)
  trial)


;###############################################################################

(defn- trigger [trial msg]
  (logging/info "TRIGGER: " msg)
  (catcher/wrap-with-log-error
    (set-terminate-scripts trial))
  (catcher/wrap-with-log-error
    (exec-ready-scripts trial)) 
  trial)

(defn- eval-watch-trigger [trial old-state new-state]
  (future 
    (catcher/wrap-with-log-error
      (when (not= (:state old-state) (:state new-state))
        (trigger trial (str (:name old-state) " : " 
                            (:state old-state) " -> " (:state new-state)))))))


;###############################################################################

(defn- set-skipped-state-if-not-finnished [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (when-not (finished? script-atom)
      (swap! script-atom #(assoc % :state "skipped"))))
  trial)

(defn- finished-less-then-x-ago? [item x]
  (boolean (when-let [finished-at (:finished_at item)]
             (time/before? (time/minus (time/now) x)
                           finished-at))))


;### watchers #################################################################

(defn- add-watchers [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (add-watch script-atom 
               :trigger 
               (fn [_ script-atom old-state new-state]
                 (eval-watch-trigger trial old-state new-state)))))

(defn- remove-watchers [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (remove-watch script-atom :trigger)))


;###############################################################################

(defn process [trial]
  (try 
    (add-watchers trial)
    (trigger trial "initial")
    (loop []
      (Thread/sleep 100)
      (let [scripts-atoms (trial/get-scripts-atoms trial)]
        (when (and (not (every? finished? scripts-atoms))
                   (or (some executing? scripts-atoms)
                       ; give the triggers a chance to evaluate: 
                       (not-empty (->> scripts-atoms 
                                       (filter finished?)
                                       (filter #(finished-less-then-x-ago? 
                                                  @% (time/seconds 1)))))))
          (recur))))
    (remove-watchers trial)
    trial
    (finally 
      (set-skipped-state-if-not-finnished trial))))

(defn abort [trial-id]
  ; TODO 
  )




;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

