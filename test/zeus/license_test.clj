(ns zeus.license-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.license :as l]))

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
