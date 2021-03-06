(ns cider-ci.ex.scripts.processor.abort
  (:require
    [cider-ci.ex.scripts.exec :as exec]
    [cider-ci.ex.scripts.processor.skipper :as scripts-skipper]
    [cider-ci.ex.trials.helper :as trials]
    [cider-ci.ex.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.core :refer :all]
    [clj-time.core :as time]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))



;### abort ####################################################################

(defn- set-to-terminate-when-executing [trial]
  (I>> identity-with-logging
       (trials/get-scripts-atoms trial)
       (filter #(-> % deref executing?))
       (filter #(-> % deref :ignore_abort not))
       (map (fn [script-atom]
              (swap! script-atom  #(assoc % :terminate true ))))
       doall))

(defn set-to-skipped-when-pending [trial]
  (I>> identity-with-logging
       (trials/get-scripts-atoms trial)
       (filter #(-> % deref pending?))
       (filter #(-> % deref :ignore-abort not))
       (map #(scripts-skipper/skip-script % "aborting"))
       doall))

(defn abort [trial]
  (set-to-skipped-when-pending trial)
  (set-to-terminate-when-executing trial))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
