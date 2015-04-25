; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
 
(ns cider-ci.ex.scripts.helper
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    ))


(defn pending? [script]
  (= "pending" (:state script)))

(defn finished? [script]
  (boolean (#{"passed" "failed" "aborted" "skipped"} (:state script))))

;(finished? {:state "passed"})
