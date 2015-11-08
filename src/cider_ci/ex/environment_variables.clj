; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
(ns cider-ci.ex.environment-variables
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [selmer.parser]
    [drtom.logbug.debug :as debug]
    [cider-ci.ex.scripts.exec.shared :refer :all]
    ))



; TODO: (name k) doesn't play well with '/'
(defn- stringify [mp]
  (map
    (fn [[k v]] [(name k) (str v)])
    mp))

(defn- remove-nil-values [mp]
  (filter
    (fn [[k v]] (not= nil v))
    mp))

(defn- upper-case-keys [mp]
  (map
    (fn [[k v]] [(clojure.string/upper-case k) v])
    mp))

;##############################################################################

;(selmer.parser/render "Hello {{NAME}}!" (clojure.walk/keywordize-keys {"NAME" "Yogthos"}))

(defn- to-elmer-params [mp]
  (->> mp
       (into {})
       clojure.walk/keywordize-keys))

(defn- process-templates [mp]
  (let [params (to-elmer-params mp)
        new-mp (map
                 (fn [[k v]]
                   [k (selmer.parser/render v params)])
                 mp)]
    (if (not= mp new-mp)
      (process-templates new-mp)
      mp)))

;##############################################################################

(defn prepare [params]
  (->> (merge { }
              {:CIDER_CI_WORKING_DIR (working-dir params)
               :CIDER_CI true
               :CONTINUOUS_INTEGRATION true}
              (:ports params)
              (:environment-variables params))
       remove-nil-values
       stringify
       upper-case-keys
       (#(if (:template-environment-variables params)
           (process-templates %) %))
       (into {})))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(remove-ns (symbol (str *ns*)))
;(debug/debug-ns *ns*)
;(debug/re-apply-last-argument #'prepare)
