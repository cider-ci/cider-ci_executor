; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.git.repository
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.utils.config :as config :refer [get-config]]
    [logbug.debug :as debug]
    [cider-ci.utils.fs :as ci-fs]
    [logbug.thrown :as thrown]
    [cider-ci.utils.http :refer [build-server-url]]
    [cider-ci.utils.system :as system]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clj-uuid]
    [clojure.pprint :as pprint]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]
    ))


;### Repo path ################################################################

(defn- canonical-repository-path [repository-url]
  (let [absolute-repos-path (-> (get-config) :repositories_dir fs/absolute)
        path (str absolute-repos-path  (File/separator)
                  (ci-fs/path-proof repository-url))]
    (assert (> (count path) (count (str absolute-repos-path))))
    path))

;### Agents ###################################################################

(defonce repository-agents-atom (atom {}))

(defn- get-or-create-repository-agent [repository-url]
  (or (@repository-agents-atom repository-url)
      ((swap! repository-agents-atom
              (fn [repository-agents repository-url]
                (if (repository-agents repository-url)
                  repository-agents
                  (conj repository-agents
                        {repository-url
                         (agent {:repository-url repository-url
                                 :repository-path (canonical-repository-path repository-url) }
                                :error-mode :continue)})))
              repository-url)
       repository-url)))


;### Core Git #################################################################

(defn- git-fetch [path repository-url]
  (system/exec-with-success-or-throw
    ["git" "fetch" "--force" "--tags" "--prune" repository-url "+*:*"]
    {:dir path
     :add-env {"GIT_SSL_NO_VERIFY" "1"}
     :watchdog (* 60 60 1000)}))

(defn- initialize-repo [repository-url proxy-url path]
  (system/exec ["rm" "-rf" path])
  (system/exec-with-success-or-throw ["git" "init" "--bare" path])
  (git-fetch path (or proxy-url repository-url))
  ;(system/exec-with-success-or-throw ["remote" "add" "origin" repository-url]{:dir path})
  )

(defn- repository-includes-commit? [path commit-id]
  "Returns false if there is an exeception!"
  (and (system/exec-with-success?  ["git" "cat-file" "-t" commit-id] {:dir path})
       (system/exec-with-success?  ["git" "ls-tree" commit-id] {:dir path})))

(defn- initialize-or-update-if-required [agent-state repository-url proxy-url commit-id]
  (let [repository-path (:repository-path agent-state)]
    (when-not (system/exec-with-success?
                ["git" "rev-parse" "--resolve-git-dir" repository-path])
      (initialize-repo repository-url proxy-url repository-path))
    (loop [update-count 1]
      (when-not (repository-includes-commit? repository-path commit-id)
        (git-fetch repository-path (or proxy-url repository-url))
        (when-not (repository-includes-commit? repository-path commit-id)
          (when (<= update-count 3)
            (Thread/sleep 250)
            (recur (inc update-count))))))
    (when-not (repository-includes-commit? repository-path commit-id)
      (throw (IllegalStateException. (str "The git commit is not present."
                                          {:repository-path repository-path :commit-id commit-id}))))
    (conj agent-state {:last-successful-update {:time (time/now) :commit-id commit-id}})))

(defn serialized-initialize-or-update-if-required
  "Returns a absolute path to bare clone which is guaranteed to contain the commit-id."
  [repository-url proxy-url commit-id]
  (if-not (and repository-url commit-id)
    (throw (java.lang.IllegalArgumentException. "serialized-initialize-or-update-if-required")))
  (-> (let [repository-agent (get-or-create-repository-agent repository-url)
            res-atom (atom nil)
            fun (fn [agent-state]
                  (try
                    (reset! res-atom
                            (dissoc (initialize-or-update-if-required
                                      agent-state repository-url proxy-url commit-id)
                                    :exception))
                    (catch Exception e
                      (logging/warn (thrown/stringify e))
                      (reset! res-atom (conj agent-state {:exception e})))
                    (finally @res-atom)))]

        (send-off repository-agent fun)
        (while (nil? @res-atom) (Thread/sleep 100))
        (when-let [exception (:exception @res-atom)]
          (throw exception))
        (:repository-path @repository-agent))
      fs/absolute
      fs/normalized
      str))

(defn- clone-to-dir [repository-path commit-id branch-name dir]
  (let [cmd (concat
              ["git" "clone" "--shared"]
              (if (clojure.string/blank? branch-name)
                []
                ["--branch" branch-name])
              [repository-path dir])]
    (system/exec-with-success-or-throw
      cmd {:watchdog (* 60 1000)})
    (system/exec-with-success-or-throw
      ["git" "checkout" commit-id] {:dir dir})
    true))

(defn serialized-clone-to-dir
  "Clones creates a shallow clone in working-dir by referencing a local clone.
  Throws an exception if creating the clone failed."
  [repository-url proxy-url commit-id branch-name working-dir]
  (let [repository-path (serialized-initialize-or-update-if-required repository-url proxy-url commit-id)
        repository-agent (get-or-create-repository-agent repository-url)
        res-atom (atom nil)
        fun (fn [agent-state]
              (try (clone-to-dir repository-path commit-id branch-name working-dir)
                   (reset! res-atom
                           (-> agent-state
                               (dissoc :exception)
                               (assoc :last-successful-clone {:time (time/now) :working-dir working-dir})))
                   (catch Exception e
                     (logging/warn (thrown/stringify e))
                     (reset! res-atom (assoc agent-state :exception e)))
                   (finally @res-atom)))]
    (send-off repository-agent fun)
    (while (nil? @res-atom) (Thread/sleep 100))
    (when-let [exception (:exception @res-atom)]
      (throw exception))))


;### Debug #####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
