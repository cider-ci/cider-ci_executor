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
    [cider-ci.ex.git :as git]
    [cider-ci.ex.reporter :as reporter]
    [cider-ci.ex.trial :as trial]
    [cider-ci.ex.web :as web]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.nrepl :as nrepl]
    [clojure.tools.logging :as logging]
    )
  (:use 
    [clj-logging-config.log4j :only (set-logger!)]
    ))

;(set-logger! :level :debug)


(defonce conf (atom {}))

(defn read-config []
  (config-loader/read-and-merge
    conf ["conf_default.yml" 
          "conf.yml"]))


(defn initialize []
  (.mkdir (File. (:working_dir @conf)))
  (.mkdir (File. (:git_repos_dir @conf))))

(defn -main
  [& args]
  (logging/info "starting -main " args)
  (read-config)
  (initialize)
  (nrepl/initialize (:nrepl @conf))
  (http/initialize (select-keys @conf [:basic_auth]))
  (git/initialize (select-keys @conf [:sudo :basic_auth :working_dir :git_repos_dir]))
  (exec/initialize (select-keys @conf [:exec :sudo]))
  (trial/initialize)
  (reporter/initialize (:reporter @conf))
  (web/initialize (select-keys @conf [:web :basic_auth])))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
