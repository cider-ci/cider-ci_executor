; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.attachments
  (:require 
    [cider-ci.utils.with :as with]

    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.stacktrace :as stacktrace]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

(defonce ^:private _put (atom nil))
(defn put [working-dir attachments base-url]
  (reset! _put [working-dir attachments base-url])
  (logging/debug put [working-dir attachments base-url])
  (let [abs-working-dir (fs/absolute (fs/file working-dir))]
    (doseq [[_ {glob :glob content-type :content-type}] attachments]
      (println glob content-type)
      (fs/with-cwd abs-working-dir
        (doseq [file (fs/glob glob)] 
          (with/suppress-and-log-warn
            (let [relative (apply str (drop (inc (count (str abs-working-dir))) (seq (str file))))
                  url (str base-url relative)]
              (logging/debug "sending as attachment: " file)
              (logging/debug "nomalized: " (fs/normalized file))
              (logging/debug "name: " (fs/name file))
              (logging/debug "base-name: " (fs/base-name file))
              (logging/debug "relative: " relative)
              (logging/debug "url: " url)
              (let [result (http-client/put url
                                            {:body file  :content-type content-type})]
                (logging/debug "request result: " result)))
            ))))))
;(apply put @_put)


