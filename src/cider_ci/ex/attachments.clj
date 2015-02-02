; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.attachments
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]
    ))


(defn path-post-fix [file abs-working-dir]
  (if (.startsWith (str file) (str abs-working-dir))
    (apply str (drop (inc (count (str abs-working-dir))) 
                     (seq (str file))))
    (apply str (drop 1 (str (fs/normalized (str file)))))
    ))

(defn put-file [file abs-working-dir base-url content-type]
  (with/logging
    (let [relative (path-post-fix file abs-working-dir) 
          url (str base-url relative)]
      (logging/debug "putting attachment" 
                     {:file file :nomalized (fs/normalized file)
                      :name (fs/name file) :base-name (fs/base-name file)
                      :relative relative :url url})
      (http/put url {:body file  :content-type content-type})
      )))


(defn put [working-dir attachments base-url]
  (let [abs-working-dir (fs/absolute (fs/file working-dir))]
    (doseq [[_ {glob :glob content-type :content-type}] attachments]
      (fs/with-cwd abs-working-dir
        (doseq [file (fs/glob glob)] 
          (put-file file abs-working-dir base-url content-type))))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

