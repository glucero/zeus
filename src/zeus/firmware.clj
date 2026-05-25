(ns zeus.firmware
  (:require [babashka.http-client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ps3-updatelist-url
  "http://dus01.ps3.update.playstation.net/update/ps3/list/us/ps3-updatelist.txt")

(defn fetch-text
  "GET `url` and return the response body as a string. Pulled out so
   tests can redef it."
  [url]
  (-> (http/get url) :body))

(defn fetch-stream
  "GET `url` as a stream. Pulled out so tests can redef it."
  [url]
  (http/get url {:as :stream}))

(defn ps3-pup-url
  "Resolve the URL of the current PS3 firmware PUP by parsing Sony's
   update-list endpoint. Returns nil if no PS3UPDAT.PUP line is found."
  []
  (let [body (fetch-text ps3-updatelist-url)
        line (->> (str/split-lines body)
                  (filter (fn [l] (re-find #"PS3UPDAT\.PUP" l)))
                  first)]
    (some->> line (re-find #"CDN=([^;]+)") second)))

(defn- stream-to-file
  [^java.io.InputStream in ^java.io.File file total chunk-size progress-fn]
  (with-open [out (io/output-stream file)]
    (let [buffer (byte-array chunk-size)]
      (loop [downloaded 0]
        (let [n (.read in buffer)]
          (if (neg? n)
            downloaded
            (do (.write out buffer 0 n)
                (let [next-total (+ downloaded n)]
                  (when (and total progress-fn) (progress-fn next-total total))
                  (recur next-total)))))))))

(defn download
  "Download `url` to `file`. Streams via a `.partial` sibling and
   renames on success. Returns the file."
  [url ^java.io.File file {:keys [progress-fn chunk-size]
                           :or {chunk-size 65536}}]
  (io/make-parents file)
  (let [{:keys [body headers]} (fetch-stream url)
        total (some-> (or (get headers "content-length")
                          (get headers "Content-Length"))
                      Long/parseLong)
        partial (io/file (str (.getPath file) ".partial"))]
    (with-open [in body]
      (stream-to-file in partial total chunk-size progress-fn))
    (.renameTo partial file)
    file))
