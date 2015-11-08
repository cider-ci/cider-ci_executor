; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts
  (:require
    [cider-ci.ex.reporter :as reporter]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.duration :refer [parse-string-to-seconds]]
    [cider-ci.utils.http :refer [build-server-url]]
    [cider-ci.utils.map :refer [deep-merge]]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))

(def ^:private terminal-states #{ "failed" "passed" "skipped" "aborted" })

;##############################################################################

(defonce ^:private patch-agents-atom (atom {}))

(defn- patch-url [trial-params script-params]
  (str (build-server-url (:patch_path trial-params))
       "/scripts/" (clj-http.util/url-encode (:key script-params))))

(defn- create-patch-agent [script-params trial-params]
  (let [swap-in-fun (fn [patch-agents]
                      (assoc patch-agents
                             (:id script-params)
                             {:agent (agent {} :error-mode :continue)
                              :url (patch-url trial-params script-params)}))]
    (swap! patch-agents-atom swap-in-fun)))

(defn- get-patch-agent-and-url [trial-params]
  (get @patch-agents-atom (:id trial-params)))

;##############################################################################

(defn- prepare-timeout [script-params]
  (let [timeout (or (:timeout script-params)
                    (:default_script_timeout (get-config)))]
    (assoc script-params :timeout
           (if (number? timeout)
             timeout
             (if (re-matches #"\d+" timeout)
               (read-string timeout)
               (parse-string-to-seconds timeout))))))

(defn- prepare-script-params [script-params trial-params]
  (deep-merge
    (select-keys trial-params
                 [:environment-variables
                  :working_dir :private_dir])
    (-> script-params
        prepare-timeout)))

;##############################################################################

(defn- send-patch [agent-state url params]
  (try
    (catcher/wrap-with-log-error
      (let [res (reporter/patch-as-json-with-retries url params)]
        (merge agent-state {:last-patch-result res})))
    (catch Throwable e
      (merge agent-state {:last-exception e}))))

(defn- send-patch-via-agent [script-atom new-state]
  (let [{patch-agent :agent url :url} (get-patch-agent-and-url @script-atom)
        fun (fn [agent-state] (send-patch agent-state url new-state))]
    (logging/debug "sending-report-off" {:url url :new-state new-state})
    (send-off patch-agent fun)))

(defn- watch [key script-atom old-state new-state]
  (logging/info :NEW-SCRIPT-STATE (json/write-str {:old-state old-state :new-state new-state}))
  (send-patch-via-agent script-atom new-state))

(defn- add-watcher [script-atom]
  (add-watch script-atom :watch watch))

(defn prepare-script [script-params trial-params]
  (create-patch-agent script-params trial-params)
  (-> (atom (prepare-script-params script-params trial-params))
      add-watcher))

; TODO: clean patch-agents and unwatch

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
