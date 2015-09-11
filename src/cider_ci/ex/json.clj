; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.json
  (:require
    [clojure.data.json]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [drtom.logbug.thrown :as thrown]
    ))

(clojure.core/extend-type org.joda.time.DateTime clojure.data.json/JSONWriter
  (-write [date-time out]
    (clojure.data.json/-write (time-format/unparse (time-format/formatters :date-time) date-time)
                              out)))

(clojure.core/extend-type Exception clojure.data.json/JSONWriter
  (-write [ex out]
    (clojure.data.json/-write (thrown/stringify ex) out)))


(clojure.core/extend-type java.util.concurrent.FutureTask clojure.data.json/JSONWriter
  (-write [future-task out]
    (clojure.data.json/-write {:done (. future-task isDone)}
                              out)))

(clojure.core/extend-type java.lang.Object clojure.data.json/JSONWriter
  (-write [obj out]
    (clojure.data.json/-write (str "Unspecified JSON conversion for: " (type obj)) out)))
