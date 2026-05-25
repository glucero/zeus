(ns zeus.license
  (:require [clojure.java.io :as io]
            [zeus.naming :as naming]
            [zeus.platforms :as p]
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
      (let [out (byte-array 16)]
        (dotimes [i 16]
          (let [b (Integer/parseInt (subs rap-hex (* i 2) (+ 2 (* i 2))) 16)]
            (aset-byte out i (unchecked-byte b))))
        out)
      (catch NumberFormatException _ nil))))

(defn- inflate
  "Decompress a zlib byte array. Returns nil on failure."
  [^bytes compressed]
  (let [inflater (Inflater.)
        buf (byte-array 4096)
        out (java.io.ByteArrayOutputStream.)]
    (.setInput inflater compressed)
    (try
      (while (not (.finished inflater))
        (let [n (.inflate inflater buf)]
          (when (zero? n)
            (throw (ex-info "inflate stalled" {})))
          (.write out buf 0 n)))
      (.toByteArray out)
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

(defn- write-bytes [^java.io.File f ^bytes data]
  (io/make-parents f)
  (with-open [out (io/output-stream f)]
    (.write out data))
  f)

(defn write-license-file
  "Write the platform-appropriate license file for `item` into `dir`.
   Returns the file written, or nil when no license is needed/available."
  [item dir]
  (let [plat (p/platform-from-source (:_source item))]
    (case plat
      (:psv :psm)
      (when-let [data (decode-zrif (:zrif item))]
        (write-bytes (io/file dir "work.bin") data))

      (:ps3 :psp)
      (when-let [data (decode-rap (:rap item))]
        (let [base (naming/content-base-name (tsv/display-name item)
                                             (tsv/content-id item))]
          (write-bytes (io/file dir (str base ".rap")) data)))

      nil)))
