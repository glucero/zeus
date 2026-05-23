(ns zeus.tsv
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defn read-tsv
  "Read a tab-separated file into a vector of maps keyed by the header row."
  [file]
  (with-open [r (io/reader file)]
    (let [[header & rows] (csv/read-csv r :separator \tab)]
      (mapv #(zipmap header %) (vec rows)))))
