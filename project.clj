; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(defproject cider-ci_executor "1.2.0"
  :description "Executor for ider-CI."
  :url "https://github.com/DrTom/cider-ci_executor"
  :license {:name "GNU Affero General Public License"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "3.0.0-beta.4"]
                 [cider-ci/clj-utils "3.0.0-beta.5"]
                 [clojure-ini "0.0.2"] 
                 [drtom/clj-uuid "0.0.8"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojars.hozumi/clj-commons-exec "1.2.0"]
                 [org.clojure/algo.generic "0.1.2"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 ]
  :source-paths ["src"]
  :profiles {:dev 
             {:dependencies [[midje "1.6.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}
             :production
             {:resource-paths ["/etc/cider-ci" "../config" "./config" "./resources"]}} 
  :plugins [[lein-midje "3.0.0"]]
  :aot [cider-ci.ex.main]
  :main cider-ci.ex.main 
  ; :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :jvm-opts ["-Xmx256m"]
  )
