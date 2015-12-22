; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts.exec.shared
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.utils.config :as config :refer [get-config]]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.set :refer [difference union]]
    [clojure.string :as string :refer [split trim]]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

(defn expired? [script-atom & ds]
  (let [timeout (:timeout @script-atom)
        started-at (:started_at @script-atom)]
    (time/after? (time/now)
                 (apply time/plus
                        (concat [started-at (time/seconds timeout)] ds)))))

(defn add-error [script-atom error]
  (swap! script-atom
         (fn [params error]
           (assoc params :error
                  (str (:error params) "\n\n" error)))
         error))

(defn merge-params [script-atom add-params]
  (swap! script-atom
         (fn [params add-params]
           (merge params add-params))
         add-params))

(defn working-dir [params]
  (.getAbsolutePath (File. (:working_dir params))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(remove-ns (symbol (str *ns*)))
;(debug/debug-ns *ns*)
