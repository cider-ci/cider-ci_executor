; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.executor.traits
  (:require
    [cider-ci.executor.utils.tags :refer :all]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.fs :refer :all]
    [cider-ci.utils.core :refer :all]
    [clojure.java.io :as io]
    [clojure.set :refer [union]]
    [clojure.string :refer [blank? lower-case split trim]]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    )
  (:import
    [org.apache.commons.lang3 SystemUtils]
    )
  )


(defonce ^:private traits (atom (sorted-set)))

(defn get-traits []
  (->> (-> @traits
           (conj (:hostname (get-config)))
           (conj (:name (get-config)))
           (conj (SystemUtils/OS_NAME))
           (conj (str (SystemUtils/OS_NAME) " " (SystemUtils/OS_VERSION))))
       (map str)
       (map clojure.string/trim)
       (filter (complement clojure.string/blank?))
       (apply sorted-set)))


(defn- set-traits [new-traits]
  (when-not (= new-traits @traits)
    (reset! traits new-traits)
    (logging/info "traits changed to " @traits)))

(defn- read-traits []
  (->> [(system-path "config" "traits.yml")
        (system-path-abs "etc" "cider-ci" "traits.yml")]
       (read-tags-from-yaml-files)
       set-traits))

(defdaemon "read-traits" 1 (read-traits))

(defn initialize []
  (start-read-traits))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
