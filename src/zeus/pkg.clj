(ns zeus.pkg
  (:require [babashka.http-client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [zeus.naming :as naming]
            [zeus.tsv :as tsv]))

(defn- missing-url? [url]
  (or (nil? url) (= "" url) (= "MISSING" url)))

(defn- pkg-filename [item]
  (str (naming/content-base-name (tsv/display-name item)
                                 (or (tsv/content-id item) "unknown"))
       ".pkg"))

(defn- existing-pkg [dir]
  (->> (.listFiles ^java.io.File dir)
       (filter (fn [^java.io.File file]
                 (and (.isFile file)
                      (str/ends-with? (.getName file) ".pkg"))))
       first))

(defn fetch-stream
  "Open a streaming HTTP GET to `url`. Returns a ring-style response with
   :body (InputStream) and :headers. Pulled out as its own var so tests
   can redef it."
  [url opts]
  (http/get url (merge {:as :stream} opts)))

(defn- stream-to-file
  "Copy `in` to `file`, calling progress-fn with [downloaded total] after
   each chunk. Returns the total bytes written."
  [^java.io.InputStream in ^java.io.File file total chunk-size progress-fn]
  (with-open [out (io/output-stream file)]
    (let [buffer (byte-array chunk-size)]
      (loop [downloaded 0]
        (let [bytes-read (.read in buffer)]
          (if (neg? bytes-read)
            downloaded
            (do (.write out buffer 0 bytes-read)
                (let [new-total (+ downloaded bytes-read)]
                  (when (and total progress-fn) (progress-fn new-total total))
                  (recur new-total)))))))))

(defn download-pkg
  "Download `item`'s PKG into `dir`. Returns the file written (or any
   pre-existing *.pkg in dir), or nil when the URL is missing.

   opts:
     :progress-fn (fn [done total])
     :chunk-size  bytes per read (default 8192)
     :timeout     http timeout in ms (default 30000)"
  [item dir {:keys [progress-fn chunk-size timeout]
             :or {chunk-size 8192 timeout 30000}}]
  (let [url (:pkg-direct-link item)]
    (when-not (missing-url? url)
      (.mkdirs ^java.io.File (io/file dir))
      (or (existing-pkg dir)
          (let [filename     (pkg-filename item)
                final-file   (io/file dir filename)
                partial-file (io/file dir (str filename ".partial"))
                {:keys [body headers]} (fetch-stream url {:timeout timeout})
                total-bytes  (some-> (or (get headers "content-length")
                                         (get headers "Content-Length"))
                                     Long/parseLong)]
            (with-open [in body]
              (stream-to-file in partial-file total-bytes chunk-size progress-fn))
            (.renameTo partial-file final-file)
            final-file)))))
