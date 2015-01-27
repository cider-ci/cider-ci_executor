; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.git
  (:import 
    [java.io File]
    )
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.exception :as exception]
    [cider-ci.utils.system :as system]
    [clj-logging-config.log4j :as logging-config]
    [clojure.pprint :as pprint]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    ))

  ;(logging-config/set-logger! :level :debug)
  ;(logging-config/set-logger! :level :info)


(defonce conf (atom {}))

;### Helper ###################################################################
(defn- insert-basic-auth [git-url]
  (when-not (re-matches #".*\/\/.*:.*@.*" git-url)
    (let [[method server_path] (clojure.string/split git-url #"\/\/")
          {username :username password :password} (:basic_auth @conf) ] 
      (str method "//" username ":" password "@" server_path))))

(defn- canonical-repository-path [prepository-id]
  (str (:git_repos_dir @conf) (File/separator) prepository-id))


;### Agents ###################################################################
(defonce repository-agents-atom (atom {}))
(defn- get-or-create-repository-agent [repository-url repository-id]
  (or (@repository-agents-atom repository-id)
      ((swap! repository-agents-atom 
              (fn [repository-agents repository-url repository-id ]
                (conj repository-agents 
                      {repository-id 
                       (agent {:commit-ids #{} 
                               :repository-id repository-id
                               :repository-url repository-url
                               :repository-path (canonical-repository-path repository-id) } 
                              :error-mode :continue)}))
              repository-url repository-id) 
       repository-id)))


;### Core Git #################################################################
(defn- create-mirror-clone [url path] 
  (system/exec-with-success-or-throw
    ["git" "clone" "--mirror" (insert-basic-auth url) path] 
    {:add-env {"GIT_SSL_NO_VERIFY" "1"}
     :watchdog (* 3 60 1000)}))

(defn- repository-includes-commit? [path commit-id]
  "Returns false if there is an exeception!" 
  (system/exec-with-success?  
    ["git" "log" "-n" "1" "--format='%H'" commit-id]
    {:dir path}))

(defn- update [path repository-url]
  (let [res (system/exec-with-success-or-throw
              ["git" "fetch" "--force" "--tags" "--prune" (insert-basic-auth repository-url) "+*:*"] 
              {:dir path
               :add-env {"GIT_SSL_NO_VERIFY" "1"}
               :watchdog (* 3 60 1000)})]))

(defn- initialize-or-update-if-required [agent-state repository-url repository-id commit-id]
  (let [repository-path (:repository-path agent-state)]
    (when-not (system/exec-with-success?
                ["git" "rev-parse" "--resolve-git-dir" repository-path])
      (create-mirror-clone repository-url repository-path))
    (when-not (repository-includes-commit? repository-path commit-id)
      (update repository-path repository-url))
    (when-not (repository-includes-commit? repository-path commit-id)
      (throw (IllegalStateException. (str "The git commit is not present." 
                                          {:repository-path repository-path :commit-id commit-id}))))
    (conj agent-state {:commit-ids (conj (:commit-ids agent-state) commit-id)})))

(defn- serialized-initialize-or-update-if-required [repository-url repository-id commit-id]
  (if-not (and repository-url repository-id commit-id)
    (throw (java.lang.IllegalArgumentException. "serialized-initialize-or-update-if-required")))
  (let [repository-agent (get-or-create-repository-agent repository-url repository-id)
        state @repository-agent]
    (when-not ((:commit-ids state) commit-id)
      (let [res-atom (atom nil)
            fun (fn [agent-state] 
                  (try 
                    (reset! res-atom 
                            (dissoc (initialize-or-update-if-required 
                                      agent-state repository-url repository-id commit-id)
                                    :exception))
                    (catch Exception e
                      (logging/warn (exception/stringify e))
                      (reset! res-atom (conj agent-state {:exception e})))
                    (finally @res-atom)))]
        (send-off repository-agent fun)
        (while (nil? @res-atom) (Thread/sleep 100))
        (when-let [exception (:exception @res-atom)]
          (throw exception))))
    (:repository-path @repository-agent)))

(defn- clone-to-dir [repository-path commit-id dir]
  (system/exec-with-success-or-throw 
    ["git" "clone" "--shared" repository-path dir] 
    {:watchdog (* 5 1000)})
  (system/exec-with-success-or-throw 
    ["git" "checkout" commit-id]
    {:dir dir})
  true)

(defn prepare-and-create-working-dir [params]
  (let [working-dir-id (:trial_id params)]
    (when (clojure.string/blank? working-dir-id)
      (throw (java.lang.IllegalArgumentException. 
               "Invalid arguments for prepare-and-create-working-dir, missing :trial_id")))
    (let [repository-path (serialized-initialize-or-update-if-required 
                            (:git_url params) (:repository_id params) (:git_commit_id params)) 
          working-dir (str (:working_dir @conf) (File/separator) working-dir-id)]
      (clone-to-dir repository-path (:git_commit_id params) working-dir)
      (when-let [user (:user (:sudo @conf))]
        (system/exec-with-success-or-throw ["chown" "-R" user working-dir]))
      working-dir)))


;### Initialize ################################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug #####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
