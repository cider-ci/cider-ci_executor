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
    [cider-ci.utils.http :refer [build-server-url]]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clj-uuid]
    [clojure.string :as string :refer [blank?]]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [me.raynes.fs :as fs]
    )
  )

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
      (when-let [clone-options (-> params :git-options :submodules :clone)]
        (submodules/update working-dir clone-options))
      working-dir)))

;### Debug #####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
