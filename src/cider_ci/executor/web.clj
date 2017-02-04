; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.web
  (:require
    [cider-ci.executor.accepted-repositories :as accepted-repositories]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.executor.certificate :as certificate]
    [cider-ci.executor.trials  :as trials]
    [cider-ci.executor.trials.state :as trials.state]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.status :as status]

    [clj-time.core :as time]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [compojure.core :as cpj]
    [compojure.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
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

(defn get-trials []
  (let [trials (trials.state/get-trials-properties)]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str trials)}
    ))

(defn get-trial [id]
  (if-let [trial (trials.state/get-trial-properties id)]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str trial)}
    {:status 404}))


;### Routing ##################################################################
(defn build-routes [context]
  (cpj/routes
    (cpj/context context []
                 (cpj/GET "/hello" [] (say-hello))
                 (cpj/GET "/trials" [] (get-trials))
                 (cpj/GET "/trials/:id" [id] (get-trial id)))))

;##### handler and routing ##############################################################

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      (compojure.handler/api (build-routes context))
      status/wrap
      routing/wrap-shutdown
      (ring.middleware.json/wrap-json-params)
      (authorize/wrap-require! {:service true})
      (http-basic/wrap {:service true})
      (routing/wrap-log-exception)))

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
  (start-server))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)

