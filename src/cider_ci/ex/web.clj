; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(ns cider-ci.ex.web
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.utils.routing :as routing]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.ex.certificate :as certificate]
    [cider-ci.ex.trial :as trial]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]
    ))


(defonce conf (atom {:host "0.0.0.0"
                     :port 8088
                     :ssl-port 8443}))


;### Web functions ############################################################
(defn say-hello []
  (str "<h1>Hello!</h1>"))

(defn ping [] 
  (logging/debug "pinging back")
  {:status 204})

(defn execute [request]
  (logging/info (str "received execution request: " request))
  (try 
    (let [trial-parameters  (clojure.walk/keywordize-keys (:json-params request))]
      (when-not (:trial_id trial-parameters) (throw (IllegalStateException. ":trial_id parameter must be present")))
      (when-not (:patch_path trial-parameters) (throw (IllegalStateException. ":patch_path parameter must be present")))
      (future (trial/execute trial-parameters))
      {:status 204})
    (catch Exception e
      (logging/error request (with-out-str (stacktrace/print-stack-trace e)))
      {:status 422 :body (str e)})))

(defn get-trials []
  (let [trials (trial/get-trials)]
    {:status 200 
     :headers {"Content-Type" "application/json"}
     :body (json/write-str trials)}
    )) 

(defn get-trial [id]
  (if-let [trial (trial/get-trial id)]
    {:status 200 
     :headers {"Content-Type" "application/json"}
     :body (json/write-str trial)}
    {:status 404}))


;### Routing ##################################################################
(defn build-routes [context]
  (cpj/routes
    (cpj/context context []
                 (cpj/GET "/hello" [] (say-hello))
                 (cpj/POST "/ping" [] (ping))
                 (cpj/POST "/execute" req (execute req))
                 (cpj/GET "/trials" [] (get-trials))
                 (cpj/GET "/trials/:id" [id] (get-trial id)))))

;##### handler and routing ############################################################## 

(defn build-main-handler [context]
  ( -> (compojure.handler/api (build-routes context))
       (routing/wrap-debug-logging 'cider-ci.ex.web)
       (routing/wrap-debug-logging 'cider-ci.ex.web)
       (ring.middleware.json/wrap-json-params)
       (routing/wrap-debug-logging 'cider-ci.ex.web)
       (auth/wrap-authenticate-and-authorize-service)
       (routing/wrap-debug-logging 'cider-ci.ex.web)
       (http-basic/wrap {:service true}) ; TODO, workaround, only check pw used both ways
       (routing/wrap-debug-logging 'cider-ci.ex.web)
       (routing/wrap-log-exception)
       ))

;### Server ###################################################################
;### TODO replaces this with the server from clj-utils
(defonce server nil)
(defn stop-server []
  (logging/info "stopping server")
  (. server stop)
  (def server nil))

(defn start-server []
  "Starts (or stops and then starts) the webserver"
  (let [keystore (certificate/create-keystore-with-certificate)
        path (.getAbsolutePath (:file keystore))
        password (:password keystore) 
        server-conf (conj {:ssl? true 
                           :keystore path
                           :key-password password
                           :join? false} 
                          (select-keys (:http @conf) [:port :host :ssl-port])) ]
    (if server (stop-server)) 
    (logging/info "starting server" server-conf)
    (def server (jetty/run-jetty 
                  (build-main-handler nil)
                  server-conf))))


;### Initialize ###############################################################
(defn initialize [new-conf]
  (reset! conf new-conf)
  (http-basic/initialize (select-keys @conf [:basic_auth]))
  (start-server))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)


