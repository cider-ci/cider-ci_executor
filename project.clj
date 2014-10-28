; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(defproject cider-ci_executor "1.0.0"
  :description "Executor for ider-CI."
  :url "https://github.com/DrTom/cider-ci_executor"
  :license {:name "GNU Affero General Public License"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "2.0.0"]
                 [cider-ci/clj-utils "2.0.0"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojars.hozumi/clj-commons-exec "1.1.0"]
                 [org.clojure/algo.generic "0.1.2"]
                 ]
  :source-paths ["src"]
  :profiles {
             :dev { :dependencies [[midje "1.6.3"]]
                   :resource-paths ["resources_dev"] 
                   }
             :production { :resource-paths [ "/etc/cider-ci_executor" ] }}
  :plugins [[lein-midje "3.0.0"]]
  :aot [cider-ci.ex.main]
  :main cider-ci.ex.main 
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  )
