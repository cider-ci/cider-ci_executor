; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.utils.state
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))


(defn- deref-or-val [x]
  (if (instance? clojure.lang.IDeref x)
    @x x))

(defn pending? [x]
  (= "pending" (:state (deref-or-val x))))

(defn executing-or-waiting? [x]
  (some #{(:state (deref-or-val x))}
        ["executing" "waiting"]))

(defn executing? [x]
  (= "executing" (:state (deref-or-val x))))

(defn finished? [x]
  (boolean (#{"passed" "failed" "aborted" "skipped"} (:state (deref-or-val x)))))

