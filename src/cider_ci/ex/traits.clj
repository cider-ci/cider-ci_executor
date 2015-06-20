; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.ex.traits
  (:require
    [clj-yaml.core :as yaml]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.daemon :as daemon]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.map :refer [deep-merge]]
    [clojure.string :refer [blank? lower-case split trim]]
    [clojure.set :refer [union]]
    [clojure.tools.logging :as logging]
    [clojure.java.io :as io]
    ))

(defonce ^:private traits (atom (sorted-set)))

(defn get-traits []
  @traits)

(defn- set-traits [new-traits]
  (when-not (= new-traits @traits)
    (reset! traits new-traits)
    (logging/info "traits changed to " @traits)))


(defn- read-traits [filenames]
  (loop [traits (sorted-set)
         filenames filenames]
    (if-let [filename (first filenames)]
      (if (.exists (io/as-file filename))
        (recur (try (let [add-on-string (slurp filename)]
                      (-> add-on-string
                          lower-case
                          (split #",")
                          (#(map trim %))
                          (#(remove blank? %))
                          (#(union traits (apply sorted-set %)))))
                    (catch Exception e
                      (logging/warn "Failed to read " filename " because " e)
                      traits))
               (rest filenames))
        (recur traits (rest filenames)))
      traits)))


;(read-traits ["./config/traits_default.txt" "./config/traits.txt"])

(daemon/define "reload-traits" start-read-traits stop-read-traits 1
  (-> (read-traits ["/etc/cider-ci/traits.txt" "./config/traits_default.txt" "./config/traits.txt"])
      set-traits))


(defn initialize []
  (stop-read-traits)
  (start-read-traits)
  )
