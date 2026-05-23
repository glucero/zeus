(ns zeus.tsv
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(def ^:private day-ms (* 24 60 60 1000))

(defn cache-valid?
  "True when mtime (epoch ms) is within expiration-days of now-ms.
   nil mtime or zero expiration is always stale."
  [mtime expiration-days now-ms]
  (boolean
   (and mtime
        (pos? expiration-days)
        (>= mtime (- now-ms (* expiration-days day-ms))))))

(defn read-tsv
  "Read a tab-separated file into a vector of maps keyed by the header row."
  [file]
  (with-open [r (io/reader file)]
    (let [[header & rows] (csv/read-csv r :separator \tab)]
      (mapv #(zipmap header %) (vec rows)))))
