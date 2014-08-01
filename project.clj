; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software. 

(defproject cider-ci_executor "1.0.0"
  :description "Executor for the Cider-CI."
  :url "https://github.com/DrTom/cider-ci_executor"
  :license {:name "GNU Affero General Public License"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [clj-http "0.9.2"]
                 [clj-logging-config "1.9.10"]
                 [clj-time "0.7.0"]
                 [clj-yaml "0.4.0"]
                 [compojure "1.1.8"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [me.raynes/fs "1.4.6"]
                 [org.bouncycastle/bcpkix-jdk15on "1.50"]
                 [org.bouncycastle/bcprov-jdk15on "1.50"]
                 [org.clojars.hozumi/clj-commons-exec "1.1.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.slf4j/slf4j-log4j12 "1.7.7"]
                 [ring "1.3.0"] 
                 [ring-basic-authentication "1.0.5"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [ring/ring-json "0.3.1"]
                 [robert/hooke "1.3.0"]
                 ]
  :source-paths ["clj-utils/src"
                 "src"]
  :profiles {
             :dev { :dependencies [[midje "1.6.3"]]
                   :resource-paths ["resources_dev"] 
                   }
             :production { :resource-paths [ "/etc/cider-ci/storage-manager" ] }}
  :plugins [[lein-midje "3.0.0"]]
  :aot [cider-ci.ex.main]
  :main cider-ci.ex.main 
  )
