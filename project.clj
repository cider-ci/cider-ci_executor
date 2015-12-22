; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci_executor "3.0.0"
  :description "Executor for ider-CI."
  :url "https://github.com/DrTom/cider-ci_executor"
  :license {:name "GNU Affero General Public License"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "3.1.0"]
                 [cider-ci/clj-utils "3.3.2"]

                 [camel-snake-kebab "0.3.2"]
                 [clj-shellwords "1.0.1"]
                 [clojure-ini "0.0.2"]
                 [drtom/clj-uuid "0.0.8"]
                 [me.raynes/fs "1.4.6"]
                 [org.apache.commons/commons-lang3 "3.4"]
                 [org.clojure/algo.generic "0.1.2"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [selmer "0.9.6"]

                 [logbug "2.0.0-beta.8"]
                 [org.clojars.hozumi/clj-commons-exec "1.2.0"]
                 ]
  :source-paths ["src"]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["config" "resources"]}
             :production
             {:resource-paths ["/etc/cider-ci" "config" "resources"]}}
  :plugins [[lein-midje "3.0.0"]]
  :aot [cider-ci.ex.main]
  :main cider-ci.ex.main
  ; :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :jvm-opts ["-Xmx256m"]
  :repl-options {:timeout  120000}
  )
