; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts
  (:require
    [cider-ci.ex.scripts.patch :as patch]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.duration :refer [parse-string-to-seconds]]
    [cider-ci.utils.core :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

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
    (select-keys trial-params [:environment_variables])
    (-> script-params prepare-timeout)
    (select-keys trial-params [:working_dir :private_dir])))

(defn prepare-script [script-params trial-params]
  (patch/create-patch-agent script-params trial-params)
  (-> (atom (prepare-script-params script-params trial-params))
      patch/add-watcher))

; TODO: clean patch-agents and unwatch

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
