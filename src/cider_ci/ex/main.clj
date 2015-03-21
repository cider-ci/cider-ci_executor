; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
;
(ns cider-ci.ex.main
  (:gen-class)
  (:import 
    [java.io File]
    )
  (:require 
    [cider-ci.ex.exec :as exec]
    [cider-ci.utils.config :as config]
    [cider-ci.ex.traits :as traits]
    [cider-ci.ex.ping :as ping]
    [cider-ci.ex.reporter :as reporter]
    [cider-ci.ex.trial :as trial]
    [cider-ci.ex.web :as web]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.with :as with]
    [clojure.tools.logging :as logging]
    ))


(defn working-dir []
  (or (-> (config/get-config) :working_dir) 
      (throw (IllegalStateException. ":working_dir config missing"))))

(defn repos-dir []
  (or (-> (config/get-config) :git_repos_dir)
      (throw (IllegalStateException. ":git_repos_dir config missing"))))

(defn basic-auth []
  (or (-> (config/get-config) :basic_auth)
      (throw (IllegalStateException. ":basic_auth config missing"))))

(defn initialize []
  (.mkdir (File. (working-dir)))
  (.mkdir (File. (repos-dir))))
                    
(defn -main [& args]
  (with/logging 
    (logging/info "starting -main " args)
    (config/initialize)
    (let [conf (config/get-config)]
      (logging/info conf)
      (traits/initialize)
      (nrepl/initialize (-> conf  :nrepl ))
      (http/initialize {:basic_auth (-> conf :basic_auth)})
      (initialize)
      (exec/initialize (select-keys conf [:exec :sudo]))
      (web/initialize {:basic_auth (basic-auth)
                       :http (-> conf :http)})
      (ping/initialize config/get-config)
      )))



;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
