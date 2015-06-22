; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.accepted-repositories
  (:require
    [cider-ci.ex.utils :refer [read-tags-from-files]]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.map :refer [deep-merge]]
    [clj-yaml.core :as yaml]
    [clojure.java.io :as io]
    [clojure.set :refer [union]]
    [clojure.string :refer [blank? lower-case split trim]]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
 ))

(defonce ^:private accepted-repositories (atom (sorted-set)))

(defn get-accepted-repositories []
  @accepted-repositories)

(defn- set-accepted-repositories [new-accepted-repositories]
  (when-not (= new-accepted-repositories @accepted-repositories)
    (reset! accepted-repositories new-accepted-repositories)
    (logging/info "accepted-repositories changed to " @accepted-repositories)))


(defn satisfied! [repository-url]
  (or (empty? @accepted-repositories)
      (some #{repository-url} @accepted-repositories)
      (throw (ex-info "The repository-url is not included in the accepted repositories."
                      {:repository-url repository-url
                       :accepted-repositories @accepted-repositories}))))

(daemon/define "reload-accepted-repositories" start-read-accepted-repositories stop-read-accepted-repositories 1
  (-> (read-tags-from-files ["/etc/cider-ci/accepted-repositories.txt"
                             "./config/accepted-repositories_default.txt"
                             "./config/accepted-repositories.txt"])
      set-accepted-repositories))

(defn initialize []
  (stop-read-accepted-repositories)
  (start-read-accepted-repositories))


