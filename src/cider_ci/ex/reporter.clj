(ns cider-ci.ex.reporter
  (:require 
    [cider-ci.ex.json]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [deep-merge]]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    ))


(defonce conf (atom {:max_retries 10
                     :retry_ms_factor 3000
                     }))



;### Patch ####################################################################
(defn patch-as-json [url params]
  (logging/info "PATCHTNG to: " url)
  (let [body (json/write-str params)
        params  {:insecure? true
                 :content-type :json
                 :accept :json 
                 :body body} ]
    (logging/debug "PATCH: " params)
    (http/patch url params)))

(defn patch-as-json-with-retries 
  ([url params]
   (patch-as-json-with-retries url params (:max_retries @conf)))
  ([url params max-retries]
   (loop [retry 0]
     (Thread/sleep (* retry retry (:retry_ms_factor @conf)))
     (let [res  (try
                  (patch-as-json url params)
                  {:url url :params params :send-status "success"}
                  (catch Exception e
                    (logging/warn "failed " (inc retry) " time to PATCH to " url " with error: " e)
                    {:url url :params params, :send-status "failed" :error e}))]
       (if (= (:send-status res) "success") 
         res
         (if (>= retry max-retries)
           res
           (recur (inc retry))))))))


;### Initialize ###############################################################
(defn initialize [new-conf]
  (reset! conf (deep-merge @conf 
                           new-conf)))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
