; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.ex.traits
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

(defonce ^:private traits (atom (sorted-set)))

(defn get-traits []
  @traits)

(defn- set-traits [new-traits]
  (when-not (= new-traits @traits)
    (reset! traits new-traits)
    (logging/info "traits changed to " @traits)))


(daemon/define "reload-traits" start-read-traits stop-read-traits 1
  (-> (read-tags-from-files ["/etc/cider-ci/traits.txt"
                             "./config/traits_default.txt"
                             "./config/traits.txt"])
      set-traits))

(defn initialize []
  (stop-read-traits)
  (start-read-traits))
