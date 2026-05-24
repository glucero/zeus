(ns zeus.tsv
  (:require [babashka.http-client :as http]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [zeus.platforms :as p]))

(defn header->key
  "Convert a TSV header label to an idiomatic kebab-case keyword.
   Example: \"PKG direct link\" -> :pkg-direct-link."
  [s]
  (-> s str/lower-case (str/replace #"\s+" "-") keyword))

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
  "Read a tab-separated file into a vector of maps with kebab-case
   keyword keys derived from the header row."
  [file]
  (with-open [r (io/reader file)]
    (let [[header & rows] (csv/read-csv r :separator \tab)
          ks (mapv header->key header)]
      (mapv #(zipmap ks %) (vec rows)))))

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

(defn content-id
  "Get a row's content id, falling back to title id."
  [row]
  (or (:content-id row) (:title-id row)))

(defn display-name
  "Get a row's display name, falling back to title."
  [row]
  (or (:name row) (:title row)))

(defn build-lookup
  "Scan all per-content-type TSVs in `cache-dir` and merge them into a
   {content-id -> row} map. Each row is tagged with :_source."
  [cache-dir]
  (reduce
   (fn [acc ct]
     (let [f (io/file cache-dir (str (name ct) ".tsv"))]
       (if-not (.exists f)
         acc
         (reduce (fn [m row]
                   (if-let [cid (content-id row)]
                     (assoc m cid (assoc row :_source ct))
                     m))
                 acc
                 (read-tsv f)))))
   {}
   p/content-types))
