; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/executor "0.0.0-PLACEHOLDER"
  :description "Executor for Cider-CI."
  :url "https://github.com/DrTom/cider-ci_executor"
  :license {:name "GNU Affero General Public License"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [camel-snake-kebab "0.3.2"]
                 [clojure-ini "0.0.2"]
                 [drtom/clj-uuid "0.0.8"]
                 [me.raynes/fs "1.4.6"]
                 [org.apache.commons/commons-lang3 "3.4"]
                 [org.clojure/algo.generic "0.1.2"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [selmer "1.0.0"]

                 [logbug "4.0.0"]
                 [org.clojars.hozumi/clj-commons-exec "1.2.0"]
                 ]
  :plugins [[cider-ci/lein_cider-ci_dev "0.2.1"]]
  :source-paths ["src"]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["config" "resources"]}}
  :aot [cider-ci.self #"cider-ci.*"]
  :main cider-ci.ex.main
  ; :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :repl-options {:timeout  120000}
  )
