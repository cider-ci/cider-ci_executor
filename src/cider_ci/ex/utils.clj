; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.ex.utils
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.set :refer [union]]
    [clojure.string :refer [blank? lower-case split trim]]
    [clojure.tools.logging :as logging]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    [clojure.java.io :as io]
    ))

(defn read-tags-from-files [filenames]
  (loop [tags (sorted-set)
         filenames filenames]
    (if-let [filename (first filenames)]
      (if (.exists (io/as-file filename))
        (recur (try (let [add-on-string (slurp filename)]
                      (-> add-on-string
                          (split #",")
                          (#(map trim %))
                          (#(remove blank? %))
                          (#(union tags (apply sorted-set %)))))
                    (catch Exception e
                      (logging/warn "Failed to read " filename " because " e)
                      tags))
               (rest filenames))
        (recur tags (rest filenames)))
      tags)))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)

