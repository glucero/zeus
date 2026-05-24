(ns zeus.license-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [zeus.license :as l])
  (:import (java.util Base64)
           (java.util.zip Deflater DeflaterOutputStream)
           (java.io ByteArrayOutputStream)))

(defn- temp-dir []
  (doto (java.io.File/createTempFile "zeus-lic" "")
    (.delete) (.mkdir) (.deleteOnExit)))

(defn- zrif-encode
  "Test helper: zlib-compress bytes then base64-encode (inverse of decode-zrif)."
  [^bytes data]
  (let [baos (ByteArrayOutputStream.)
        dos (DeflaterOutputStream. baos (Deflater.))]
    (.write dos data)
    (.close dos)
    (.encodeToString (Base64/getEncoder) (.toByteArray baos))))

(deftest decode-rap
  (testing "valid 32-char hex returns 16 bytes"
    (let [hex "0123456789abcdef0123456789abcdef"
          out (l/decode-rap hex)]
      (is (= 16 (count out)))
      (is (= 0x01 (bit-and (aget out 0) 0xff)))
      (is (= 0xef (bit-and (aget out 15) 0xff)))))
  (testing "uppercase hex also works"
    (is (= 16 (count (l/decode-rap "0123456789ABCDEF0123456789ABCDEF")))))
  (testing "nil and empty return nil"
    (is (nil? (l/decode-rap nil)))
    (is (nil? (l/decode-rap ""))))
  (testing "sentinel strings return nil"
    (is (nil? (l/decode-rap "MISSING")))
    (is (nil? (l/decode-rap "NOT REQUIRED"))))
  (testing "wrong length returns nil"
    (is (nil? (l/decode-rap "abcd")))
    (is (nil? (l/decode-rap (apply str (repeat 64 "a"))))))
  (testing "non-hex returns nil"
    (is (nil? (l/decode-rap "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz")))))

(deftest decode-zrif
  (testing "round-trips a known payload"
    (let [payload (.getBytes "work.bin contents")
          encoded (zrif-encode payload)
          decoded (l/decode-zrif encoded)]
      (is (= (seq payload) (seq decoded)))))
  (testing "nil and sentinels return nil"
    (is (nil? (l/decode-zrif nil)))
    (is (nil? (l/decode-zrif "")))
    (is (nil? (l/decode-zrif "MISSING")))
    (is (nil? (l/decode-zrif "NOT REQUIRED"))))
  (testing "garbage returns nil instead of throwing"
    (is (nil? (l/decode-zrif "not-valid-base64-!!!")))
    (is (nil? (l/decode-zrif "aGVsbG8=")))))

(def valid-rap "0123456789abcdef0123456789abcdef")

(deftest write-license-file
  (testing "psv writes zRIF payload to work.bin"
    (let [d (temp-dir)
          payload (.getBytes "vita-license")
          item {:_source :psv_games
                "Content ID" "PCSE00001"
                "Name" "Vita Game"
                "zRIF" (zrif-encode payload)}
          out (l/write-license-file item d)]
      (is (= "work.bin" (.getName out)))
      (is (= (seq payload) (seq (.readAllBytes (io/input-stream out)))))))
  (testing "ps3 writes RAP bytes to '<base>.rap'"
    (let [d (temp-dir)
          item {:_source :ps3_games "Content ID" "NPUB12345"
                "Name" "PS3 Game" "RAP" valid-rap}
          out (l/write-license-file item d)]
      (is (= "PS3 Game [NPUB12345].rap" (.getName out)))
      (is (= 16 (count (.readAllBytes (io/input-stream out)))))))
  (testing "ps3 falls back to '<content-id>.rap' when name is missing"
    (let [d (temp-dir)
          item {:_source :ps3_games "Content ID" "NPUB12345" "RAP" valid-rap}
          out (l/write-license-file item d)]
      (is (= "NPUB12345.rap" (.getName out)))))
  (testing "psx returns nil (no license needed)"
    (is (nil? (l/write-license-file {:_source :psx_games} (temp-dir)))))
  (testing "missing license data returns nil"
    (is (nil? (l/write-license-file {:_source :ps3_games "RAP" "MISSING"
                                     "Content ID" "X"}
                                    (temp-dir))))
    (is (nil? (l/write-license-file {:_source :psv_games "zRIF" ""
                                     "Content ID" "X"}
                                    (temp-dir))))))

