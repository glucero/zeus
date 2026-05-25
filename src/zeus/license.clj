(ns zeus.license
  (:require [clojure.java.io :as io]
            [zeus.naming :as naming]
            [zeus.platforms :as platforms]
            [zeus.tsv :as tsv])
  (:import (java.util Base64)
           (java.util.zip Inflater)))

(def ^:private sentinels #{"MISSING" "NOT REQUIRED" ""})

(defn decode-rap
  "Hex-decode a 32-char RAP string to a 16-byte array.
   Returns nil for nil, sentinel strings, or invalid hex."
  [rap-hex]
  (when (and (string? rap-hex)
             (not (sentinels rap-hex))
             (= 32 (count rap-hex)))
    (try
      (let [decoded (byte-array 16)]
        (dotimes [i 16]
          (let [byte-value (Integer/parseInt
                            (subs rap-hex (* i 2) (+ 2 (* i 2))) 16)]
            (aset-byte decoded i (unchecked-byte byte-value))))
        decoded)
      (catch NumberFormatException _ nil))))

(defn- inflate
  "Decompress a zlib byte array. Returns nil on failure."
  [^bytes compressed]
  (let [inflater (Inflater.)
        buffer (byte-array 4096)
        out-stream (java.io.ByteArrayOutputStream.)]
    (.setInput inflater compressed)
    (try
      (while (not (.finished inflater))
        (let [bytes-written (.inflate inflater buffer)]
          (when (zero? bytes-written)
            (throw (ex-info "inflate stalled" {})))
          (.write out-stream buffer 0 bytes-written)))
      (.toByteArray out-stream)
      (catch Exception _ nil)
      (finally (.end inflater)))))

(defn decode-zrif
  "Base64-decode a zRIF string, then zlib-inflate to a byte array.
   Returns nil for nil, sentinel strings, or any decode/inflate failure."
  [zrif]
  (when (and (string? zrif) (not (sentinels zrif)))
    (try
      (inflate (.decode (Base64/getDecoder) zrif))
      (catch IllegalArgumentException _ nil))))

(defn- write-bytes [^java.io.File file ^bytes data]
  (io/make-parents file)
  (with-open [out (io/output-stream file)]
    (.write out data))
  file)

(defn write-license-file
  "Write the platform-appropriate license file for `item` into `dir`.
   Returns the file written, or nil when no license is needed/available."
  [item dir]
  (let [platform (platforms/platform-from-source (:_source item))]
    (case platform
      (:psv :psm)
      (when-let [data (decode-zrif (:zrif item))]
        (write-bytes (io/file dir "work.bin") data))

      (:ps3 :psp)
      (when-let [data (decode-rap (:rap item))]
        (let [base (naming/content-base-name (tsv/display-name item)
                                             (tsv/content-id item))]
          (write-bytes (io/file dir (str base ".rap")) data)))

      nil)))
