; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.git.submodules
  (:require 
    [cider-ci.ex.git.repository :as repository]
    [cider-ci.utils.config :as config :refer [get-config]]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.system :as system]
    [clj-logging-config.log4j :as logging-config]
    [clojure-ini.core :as ini]
    [clojure.tools.logging :as logging]
    [clojure.string :as string]
    [me.raynes.fs :as fs]
    ))


(defn filter-sha1-chars [s]
  (-> s 
      (string/split #"")
      ((fn [s] (filter #(re-matches #"[a-f0-9]" %) s)))
      string/join))

(defn get-commit-id-of-submodule [dir relative-submodule-path]
  (-> 
    (system/exec-with-success-or-throw 
      ["git" "submodule" "status"  relative-submodule-path] 
      {:dir dir :watchdog 1000 })
    :out
    string/trim
    (string/split #"\s+")
    first
    filter-sha1-chars))


(defn gitmodules-conf [dir]
  (let [path (str dir "/.gitmodules")]
    (if (fs/exists? path)
      (ini/read-ini path :keywordize? true)
      {})))

(declare update)

(defn update-submodule [dir path bare-dir]
  (system/exec-with-success-or-throw 
    ["git" "submodule" "update" "--init" "--reference" bare-dir path]
    {:dir dir :watchdog 10000})
  (update (str dir (java.io.File/separator) path)))

(defn update [dir]
  (doseq [[_ submodule] (gitmodules-conf dir)]
    (logging/debug submodule)
    (let [url (:url submodule)
          path (:path submodule)
          commit-id (get-commit-id-of-submodule dir path)
          bare-dir (repository/serialized-initialize-or-update-if-required url commit-id)]
      (update-submodule dir path bare-dir))))

;### Debug #####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
