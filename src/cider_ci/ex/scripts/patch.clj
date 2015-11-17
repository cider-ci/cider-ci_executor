(ns cider-ci.ex.scripts.patch
  (:require
    [cider-ci.ex.reporter :as reporter]
    [cider-ci.utils.http :refer [build-server-url]]
    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
  ))

(defonce ^:private patch-agents-atom (atom {}))

(defn- patch-url [trial-params script-params]
  (str (build-server-url (:patch_path trial-params))
       "/scripts/" (clj-http.util/url-encode (:key script-params))))

(defn create-patch-agent [script-params trial-params]
  (let [swap-in-fun (fn [patch-agents]
                      (assoc patch-agents
                             (:id script-params)
                             {:agent (agent {} :error-mode :continue)
                              :url (patch-url trial-params script-params)}))]
    (swap! patch-agents-atom swap-in-fun)))

(defn- get-patch-agent-and-url [trial-params]
  (get @patch-agents-atom (:id trial-params)))


;##############################################################################

(defn- send-patch [agent-state url params]
  (try
    (catcher/wrap-with-log-error
      (let [res (reporter/patch-with-retries :json url params)]
        (merge agent-state {:last-patch-result res})))
    (catch Throwable e
      (merge agent-state {:last-exception e}))))

(defn- send-patch-via-agent [script-atom new-state]
  (let [{patch-agent :agent url :url} (get-patch-agent-and-url @script-atom)
        fun (fn [agent-state] (send-patch agent-state url new-state))]
    (send-off patch-agent fun)))

(defn send-field-patch [agent-state url value]
  (try
    (catcher/wrap-with-log-error
      (let [res (reporter/patch-with-retries :text url value)]
        (merge agent-state {:last-patch-result res})))
    (catch Throwable e
      (merge agent-state {:last-exception e}))))

(defn send-field-patch-via-agent [script-atom field value]
  (let [{patch-agent :agent url :url} (get-patch-agent-and-url @script-atom)
        url (str url "/" (name field))
        fun (fn [agent-state] (send-field-patch agent-state url value))]
    (send-off patch-agent fun)))


;##############################################################################

(defn- watch [key script-atom old-state new-state]
  (logging/debug :NEW-SCRIPT-STATE (json/write-str {:old-state old-state :new-state new-state}))
  (send-patch-via-agent script-atom new-state))

(defn add-watcher [script-atom]
  (add-watch script-atom :watch watch))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
