; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 
 
(ns cider-ci.ex.fs.last-access-time
(:import 
    [java.io File]
    [java.nio.file Files Paths]
    [java.nio.file.attribute BasicFileAttributes PosixFileAttributes]
    )
  (:require 
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.coerce :as time-coerce]
    [clj-time.core :as time]
    [clojure.java.io :refer [file]]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as clj-fs]
  ))



(defn- path-to-posix-attributes [path]
  (Files/readAttributes path 
                        PosixFileAttributes  
                        (into-array java.nio.file.LinkOption [])))

(defn- to-file [file-or-path]
  (if (= (type file-or-path) File) 
    file-or-path 
    (File. file-or-path)))

(defn- file-last-access-time [_file]
  (let [file (to-file _file)
        path (.toPath file)] 
    (-> path path-to-posix-attributes .lastAccessTime)))

(defn- creation-time [_file]
  (let [file (to-file _file)
        path (.toPath file)] 
    (-> path path-to-posix-attributes .creationTime)))

(defn last-access-time 
  "Returns the last (i.e. most recent) access time 
  of all files within dir."
  [dir]
  (let [paths (file-seq dir)
        files (filter #(.isFile %) paths)
        access-times (map file-last-access-time files)
        access-times-millis (map #(.toMillis %) access-times)]
    (try ; safe guard, if something is weired with the directory structure
         (with/log-error
           (time-coerce/from-long
             (if (>= (count files) 1)
               (reduce max access-times-millis)
               (-> dir creation-time .toMillis))))
         (catch Exception _ (time/now)))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
