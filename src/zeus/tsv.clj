(ns zeus.tsv
  (:require [babashka.http-client :as http]
            [clojure.data.csv :as csv]
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

(defn fetch-bytes
  "Fetch the URL and return the response body as a byte array.
   Pulled out as its own var so tests can redef it."
  [url]
  (:body (http/get url {:as :bytes})))

(defn download-tsv
  "Download `:url` to `:cache-file` unless the cache is still fresh.
   :force? bypasses the freshness check. Returns the cache-file."
  [{:keys [url cache-file expiration-days force?]}]
  (let [mtime (when (.exists cache-file) (.lastModified cache-file))]
    (when (or force?
              (not (cache-valid? mtime expiration-days
                                 (System/currentTimeMillis))))
      (io/make-parents cache-file)
      (with-open [out (io/output-stream cache-file)]
        (.write out ^bytes (fetch-bytes url))))
    cache-file))
