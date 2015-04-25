; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
 
(ns cider-ci.ex.trial.sweeper
  (:require 
    [cider-ci.ex.fs.last-access-time :refer [last-access-time]]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon]
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

(defn- get-trial-dir-retention-period []
  (time/minutes (or (-> (get-config) :working_dir_trial_retention_time_minutes)
                    30)))

(defn- to-be-deleted? [dir]
  (time/before?  
    (last-access-time dir)
    (time/minus (time/now) 
                (get-trial-dir-retention-period))))

(defn- trial-dirs-to-be-deleted []
  (->> (clj-fs/list-dir (get-working-dir))
       (filter clj-fs/directory?)
       (filter to-be-deleted?)))

(defn- delete [dir]
  (catcher/wrap-with-suppress-and-log-warn 
    (clj-fs/delete-dir dir)))

(defn- delete-out-of-date-trial-dirs []
  (doseq [dir (trial-dirs-to-be-deleted)]
    (delete dir)))


(daemon/define "trial-working-dir-sweeper" 
  start-trial-working-dir-sweeper
  stop-trial-working-dir-sweeper
  60
  (delete-out-of-date-trial-dirs))

(defn initialize []
  (start-trial-working-dir-sweeper))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
