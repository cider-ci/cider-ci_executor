; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scripts.exec.terminator
  (:import
    [java.io File]
    [org.apache.commons.exec ExecuteWatchdog]
    [org.apache.commons.lang3 SystemUtils]
    )
  (:require
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.fs :as ci-fs]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.set :refer [difference union]]
    [clojure.string :as string :refer [split trim]]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    ))

;### termination ##############################################################
; It seems not possible to guarantee that all subprocesses are killed.  The
; default strategy is to rely on "Apache Commons Exec"  which sometimes works
; but is unreliable in many cases, e.g. when the script starts in a new
; sub-shell.  On Linux and MacOS we recursively find all subprocesses via `ps`,
; see also `ps axf -o user,pid,ppid,pgrp,args`, and then kill those. This works
; well unless double forks are used which are nearly impossible to track with
; reasonable effort.

(defn create-watchdog []
  (ExecuteWatchdog. (ExecuteWatchdog/INFINITE_TIMEOUT) ))

(defn- get-child-pids-linux [pids]
  (try (-> (commons-exec/sh
             ["ps" "--no-headers" "-o" "pid" "--ppid" (clojure.string/join "," pids)])
           deref
           :out
           trim
           (split #"\n")
           (#(map trim %))
           set)
       (catch Exception _
         (set []))))

(defn- get-child-pids-mac-os [pids]
  (try (->> (-> (commons-exec/sh ["ps" "x" "-o" "pid ppid"])
                deref
                :out
                trim
                (split #"\n")
                (#(map trim %))
                rest)
            (map #(split % #"\s+"))
            (filter #(some (-> % second list set) pids))
            (map first)
            set)
       (catch Exception _
         (set []))))

(defn- get-child-pids [pids]
  (case (System/getProperty "os.name")
    "Linux" (get-child-pids-linux pids)
    "Mac OS X" (get-child-pids-mac-os pids)
    (throw (IllegalStateException. (str "get-child-pids is not supported for "
                                         (System/getProperty "os.name"))))))

(defn- add-descendant-pids [pids]
  (logging/debug 'add-descendant-pids pids)
  (let [child-pids (get-child-pids pids)
        result-pids (union pids child-pids)]
    (logging/debug {:pids pids :result-pids result-pids})
    (if (= pids result-pids)
      result-pids
      (add-descendant-pids result-pids))))

(defn- pid-file-path [params]
  (let [private-dir (-> params :private_dir)
        script-name (-> params :name)]
    (str private-dir (File/separator) (ci-fs/path-proof script-name) ".pid")))

(defn- terminate-via-process-tree [exec-future script-atom]
  (let [working-dir  (-> script-atom deref :working_dir)
        pid (-> (pid-file-path @script-atom) slurp clojure.string/trim)
        pids (add-descendant-pids #{pid})
        descendant-pids (difference pids #{pid})]
    (commons-exec/sh
      (concat ["kill" "-KILL"]
              (into [] descendant-pids)))))

(defn- terminate-via-commons-exec-watchdog [exec-future script-atom]
  (.destroyProcess (:watchdog @script-atom)))

(defn terminate [exec-future script-atom]
  (case (System/getProperty "os.name")
    ("Linux" "Mac OS X")(terminate-via-process-tree exec-future script-atom)
    (terminate-via-commons-exec-watchdog exec-future script-atom)
    ))

(defn preper-terminate-script-prefix [params]
  (cond
    SystemUtils/IS_OS_UNIX (str "echo $$ > '"(pid-file-path params)"' && ")
    :else ""))

