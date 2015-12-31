; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ex.scratch
  (:require
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [cider-ci.ex.trials :as trials]
    [clj-commons-exec :as commons-exec]
    [clojure.java.io :as io]
    [clojure.reflect]
    )
  (:import
    [java.io
     ByteArrayOutputStream
     Closeable
     File
     InputStream
     OutputStream
     PipedInputStream
     PipedOutputStream
     ]
    ))


(def write-messages-cmd
  "#!/usr/bin/env bash
  set -eux
  for i in 1 2 3 4 5
  do
  sleep 1
  echo \"Message $i\"
  done
  ")



(def is (PipedInputStream. 1024))

(defn available [] (.available is))

(def buffer (make-array Byte/TYPE 1024))

;(.read is buffer 0 (available))
;(String. buffer "UTF-8")


(def os (PipedOutputStream. is))

(defn run []
  (let [sh (commons-exec/sh
             ["bash" "-c" write-messages-cmd]
             {:out os
              :close-out? true
              }
             )]
    {:sh sh}))


;##############################################################################

(def buffer-size 1024)

(defn build-out-reader [output-handler]
  (let [buffer (make-array Byte/TYPE buffer-size)
        is (PipedInputStream. buffer-size)
        os (PipedOutputStream. is)]
    (future
      (catcher/snatch {}
        (with-open [is is]
          (loop []
            (logging/info "looping")
            (let [bytes-read (.read is buffer 0 buffer-size)
                  output (String. buffer "UTF-8")]
              (when (not= bytes-read -1)
                (logging/info "READ " bytes-read " : "  output)
                (output-handler output)
                (Thread/sleep 200)
                (recur)))))))
    os))


(defn run2 []
  (let [sh (commons-exec/sh
             ["bash" "-c" write-messages-cmd]
             {:out (build-out-reader #(println %))
              :close-out? true}
             )]
    {:sh sh}))

