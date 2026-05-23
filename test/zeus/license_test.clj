(ns zeus.license-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.license :as l])
  (:import (java.util Base64)
           (java.util.zip Deflater DeflaterOutputStream)
           (java.io ByteArrayOutputStream)))

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

