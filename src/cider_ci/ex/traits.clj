; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.ex.traits
  (:require
    [cider-ci.ex.utils.tags :refer :all]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.fs :refer :all]
    [cider-ci.utils.map :refer [deep-merge]]
    [clojure.java.io :as io]
    [clojure.set :refer [union]]
    [clojure.string :refer [blank? lower-case split trim]]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))

(defonce ^:private traits (atom (sorted-set)))

(defn get-traits []
  @traits)

(defn- set-traits [new-traits]
  (when-not (= new-traits @traits)
    (reset! traits new-traits)
    (logging/info "traits changed to " @traits)))

(defn- read-traits []
  (->> [(system-path "config" "traits.yml")
        (system-path ".." "config" "traits.yml")]
       (read-tags-from-yaml-files)
       set-traits))

(defdaemon "read-traits" 1 (read-traits))

(defn initialize []
  (start-read-traits))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
