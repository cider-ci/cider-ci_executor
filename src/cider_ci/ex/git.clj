; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.git
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.ex.git.repository :as repository]
    [cider-ci.ex.git.submodules :as submodules]
    [cider-ci.utils.config :as config :refer [get-config]]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [cider-ci.utils.http :refer [build-server-url]]
    [cider-ci.utils.system :as system]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clj-uuid]
    [clojure.pprint :as pprint]
    [clojure.string :as string :refer [blank?]]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]
    )
  (:import
    [org.apache.commons.lang3 SystemUtils]
    ))

;### Core Git #################################################################

(defn prepare-and-create-working-dir [params]
  (let [working-dir-id (:trial_id params)
        commit-id (:git_commit_id params)
        repository-url (or (:git_url params)
                           (build-server-url (:git_path params)))]
    (assert (not (blank? working-dir-id)))
    (assert (not (blank? commit-id)))
    (assert (not (blank? repository-url)))
    (let [working-dir (-> (str (:working_dir (get-config))
                               (File/separator) working-dir-id)
                          fs/absolute
                          fs/normalized
                          str)]
      (repository/serialized-clone-to-dir repository-url commit-id working-dir)
      (when (-> params :git-options :submodules :clone)
        (submodules/update working-dir))
      (when-let [user (-> (get-config) :exec_user)]
        (cond
          SystemUtils/IS_OS_UNIX (system/exec-with-success-or-throw
                                   ["chown" "-R" user working-dir])
          SystemUtils/IS_OS_WINDOWS (system/exec-with-success-or-throw
                                      ["icacls" working-dir "/grant" (str user ":(OI)(CI)F")])))
      working-dir)))

;### Debug #####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
