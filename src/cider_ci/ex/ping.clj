; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.ex.ping
  (:require
    [cider-ci.ex.traits :as traits]
    [cider-ci.utils.daemon :as daemon]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.http :as http :refer [build-service-url]]
    [cider-ci.utils.map :refer [deep-merge]]
    [clj-yaml.core :as yaml]
    [clojure.java.io :as io]
    [clojure.tools.logging :as logging]
    [clojure.data.json :as json]
    ))


;Runtime.getRuntime().availableProcessors();
;(.availableProcessors(Runtime/getRuntime))



(defn ^:dynamic get-config [] {})

(defn ping []
  (try
    (let [config (get-config)
          url (build-service-url :dispatcher "/ping")
          traits (into [] (traits/get-traits))
          max-load (or (:max_load config)
                       (.availableProcessors(Runtime/getRuntime)))
          data {:traits traits
                :max_load max-load}]
      (http/post url {:body (json/write-str data)})
      (Thread/sleep (* 1000 3)))
    (catch Exception e
      (logging/warn "ping to url failed " e))))

(daemon/define "ping" start-ping stop-ping 3
  (ping))

(defn initialize [get-config-fn]
  (def ^:dynamic get-config get-config-fn)
  (start-ping))
