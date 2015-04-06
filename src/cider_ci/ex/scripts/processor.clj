; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.ex.scrips.processor
  (:require 
    [cider-ci.ex.exec :as exec]
    [cider-ci.utils.debug :as debug]
    [clj-commons-exec :as commons-exec]
    [clojure.tools.logging :as logging]
    ))

(defonce ^:private processors (atom {}))


(defn get-or-create-processor [trial-id]
  )
; # Facts
; * an execution can be terminated through its watchdog by calling  destroyProcess()


; ways to do: 
; 
; when some script process is done, trigger others (for the same trial)
; * how do we know which trial (or, do we just check all of them?, there
;   can't be that many)
; 
; loop and check all the time
;  
; use a agent per trial at any rate; watch out for deadlocks 
 

;### public fns ###############################################################

(defn process [trial]
  )

(defn abort [trial-id]
  )

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
