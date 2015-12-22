(ns cider-ci.ex.reporter
  (:require
    [cider-ci.ex.json]
    [logbug.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [deep-merge]]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [clojure.walk]
    ))

(defonce conf (atom {:max_retries 10
                     :retry_ms_factor 3000
                     }))


;### Patch ####################################################################

(defn patch [content-type url content]
  (let [body (case content-type
               :json (json/write-str content)
               :text (str content))
        params  {:insecure? true
                 :content-type content-type
                 :accept :json
                 :body body} ]
    (http/patch url params)))

(defn patch-with-retries
  ([content-type url content]
   (patch-with-retries content-type url content (:max_retries @conf)))
  ([content-type url content max-retries]
   (loop [retry 0]
     (Thread/sleep (* retry retry (:retry_ms_factor @conf)))
     (let [res  (try
                  (patch content-type url content)
                  {:url url :content content :send-status "success"}
                  (catch Exception e
                    (logging/warn "failed " (inc retry) " time to PATCH to " url " with error: " e)
                    {:url url :content content, :send-status "failed" :error e}))]
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
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'patch-as-json)
;
;(debug/debug-ns *ns*)
