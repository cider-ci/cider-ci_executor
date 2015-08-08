; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.trials
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.ex.attachments :as attachments]
    [cider-ci.ex.git :as git]
    [cider-ci.ex.port-provider :as port-provider]
    [cider-ci.ex.reporter :as reporter]
    [cider-ci.ex.result :as result]
    [cider-ci.ex.scripts.processor]
    [cider-ci.ex.trials.helper :refer :all]
    [cider-ci.ex.trials.state :refer [create-trial]]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.http :refer [build-server-url]]
    [cider-ci.utils.map :refer [deep-merge]]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.pprint :as pprint]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [robert.hooke :as hooke]
[cider-ci.ex.accepted-repositories :as accepted-repositories]
    ))


;#### prepare trial ###########################################################
(defn- set-and-send-start-params [trial]
  (let [params-atom (get-params-atom trial)
        report-agent (:report-agent trial)]
    (swap! params-atom (fn [params] (conj params {:state "executing"})))
    (send-patch-via-agent trial (select-keys @params-atom [:state :started_at])))
  trial)

(defn- prepare-script [k script-params trial-params]
  (atom (conj {}
              (deep-merge (select-keys trial-params
                                       [:environment-variables
                                        :job_id
                                        :trial_id
                                        :working_dir])
                          script-params)
              (when-not (:name script-params)
                {:name (name k)})
              {:state "pending"
               :key (name k)})))

(defn- prepare-and-insert-scripts [trial]
  (let [params-atom (get-params-atom trial)
        initial-scripts (:scripts @params-atom)
        script-atoms (->> initial-scripts
                          (map (fn [[k v]]
                                 [k (prepare-script k v @params-atom)]))
                          (into {}))]
    (swap! params-atom #(conj %1 {:scripts %2}) script-atoms))
  trial)


;#### stuff ###################################################################

(defn- create-and-insert-working-dir [trial]
  "Creates a working dir (populated with the checked out git repo),
  adds the :working_dir key to the params-atom and sets the corresponding
  value. Returns the (modified) trial)"
  (let [params-atom (get-params-atom trial)
        working-dir (git/prepare-and-create-working-dir @params-atom)]
    (swap! params-atom
           #(assoc %1 :working_dir %2)
           working-dir))
  trial)

(defn- occupy-and-insert-ports [trial]
  "Occupies free ports according to the :ports directive,
  adds the corresponding :ports value to each script and
  also returns the ports."
  (let [params-atom (get-params-atom trial)
        ports (into {} (map (fn [[port-name port-params]]
                              [port-name (port-provider/occupy-port
                                           (or (:inet_address port-params) "localhost")
                                           (:min port-params)
                                           (:max port-params))])
                            (:ports @params-atom)))]
    (doseq [script-atom (get-scripts-atoms trial)]
      (swap! script-atom #(conj %1 {:ports %2}) ports))
    ports))

(defn put-attachments [trial]
  (let [params-atom (get-params-atom trial)
        working-dir (get-working-dir trial)]
    (attachments/put working-dir
                     (:trial-attachments @params-atom)
                     (build-server-url (:trial-attachments-path @params-atom)))
    (attachments/put working-dir
                     (:tree-attachments @params-atom)
                     (build-server-url (:tree-attachments-path @params-atom))))
  trial)

(defn set-final-state [trial]
  (let [passed?  (->> (get-scripts-atoms trial)
                      (filter #(-> % deref :ignore-state not))
                      (every? #(= "passed" (-> % deref :state))))
        final-state (if passed? "passed" "failed")]
    (swap! (get-params-atom trial)
           #(conj %1 {:state %2, :finished_at (time/now)})
           final-state))
  trial)

(defn set-result [trial]
  (let [params-atom (get-params-atom trial)
        working-dir (get-working-dir trial)]
    (result/try-read-and-merge working-dir params-atom))
  trial)

(defn release-ports [ports]
  (doseq [[_ port] ports]
    (port-provider/release-port port)))

(defn send-final-result [trial]
  (let [params (dissoc @(get-params-atom trial) :scripts)]
    (send-patch-via-agent trial params))
  trial)


;#### execute #################################################################
(defn execute [params]
  (logging/info execute [params])
  (let [trial (create-trial params)]
    (try (accepted-repositories/assert-satisfied (:git_url params))
         (->> trial
              create-and-insert-working-dir
              prepare-and-insert-scripts)
         (let [ports (occupy-and-insert-ports trial)]
           (try (->> trial
                     set-and-send-start-params
                     cider-ci.ex.scripts.processor/process
                     put-attachments
                     set-final-state
                     set-result
                     send-final-result)
                (finally (release-ports ports)
                         trial)))
         (catch Exception e
           (let [e-str (thrown/stringify e)]
             (swap! (get-params-atom trial)
                    (fn [params] (conj params
                                       {:state "failed",
                                        :finished_at (time/now)
                                        :error e-str })))
             (logging/error e-str)
             (send-final-result trial)))
         (finally trial))
    trial))


;#### initialize ###############################################################
(defn initialize []
  )


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)