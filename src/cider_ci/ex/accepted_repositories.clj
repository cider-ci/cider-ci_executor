; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.accepted-repositories
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

(defonce ^:private accepted-repositories (atom (sorted-set)))

(defn get-accepted-repositories []
  @accepted-repositories)

(defn- set-accepted-repositories [new-accepted-repositories]
  (when-not (= new-accepted-repositories @accepted-repositories)
    (reset! accepted-repositories new-accepted-repositories)
    (logging/info "accepted-repositories changed to " @accepted-repositories)))

(defn assert-satisfied [repository-url]
  (when (clojure.string/blank? repository-url)
    (throw (ex-info "The repository-url may not be empty."
                    {:status 422
                     :repository-url repository-url
                     :accepted-repositories @accepted-repositories})))
  (or (empty? @accepted-repositories)
      (some #{repository-url} @accepted-repositories)
      (throw (ex-info "The repository-url is not included in the accepted repositories."
                      {:status 403
                       :repository-url repository-url
                       :accepted-repositories @accepted-repositories}))))

(defn- reload-accepted-repositories []
  (->> [(system-path ".." "config" "accepted-repositories.yml")
        (system-path "config" "accepted-repositories.yml")]
       (read-tags-from-yaml-files)
       set-accepted-repositories))

(defdaemon "reload-accepted-repositories" 1
  (reload-accepted-repositories))

(defn initialize []
  (start-reload-accepted-repositories))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
