; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.trials.working-dir-sweeper
  (:require
    [cider-ci.ex.trials.state]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.java.io :refer [file]]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as clj-fs]
    )
  (:import
    [java.io File]
    [java.nio.file Files Paths]
    [java.nio.file.attribute BasicFileAttributes PosixFileAttributes]
    [org.apache.commons.io FilenameUtils]
    ))

(defn- get-working-dir []
  (-> (get-config) :working_dir clj-fs/absolute clj-fs/normalized))

(defn- get-trial-dirs []
  (->> (clj-fs/list-dir (get-working-dir))
       (filter clj-fs/directory?)))

(defn- delete [dir]
  (catcher/wrap-with-suppress-and-log-warn
    (clj-fs/delete-dir dir)))

(defn- delete-orphans []
  (doseq [working-dir (get-trial-dirs)]
    (when-let [base-name (clj-fs/base-name working-dir)]
      (when-not (cider-ci.ex.trials.state/get-trial base-name)
        (logging/info "deleting working-dir " working-dir)
        (delete working-dir)))))

(defdaemon "trial-working-dir-sweeper"
  1 (delete-orphans))

(defn initialize []
  (start-trial-working-dir-sweeper))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)