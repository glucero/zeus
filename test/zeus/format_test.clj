(ns zeus.format-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.format :as f]))

(deftest format-size
  (testing "converts bytes string to GB with two decimals"
    (is (= "1.00 GB" (f/format-size "1073741824")))
    (is (= "0.50 GB" (f/format-size (str (long (* 0.5 1073741824))))))
    (is (= "2.50 GB" (f/format-size (str (long (* 2.5 1073741824)))))))
  (testing "passes through non-numeric or sentinel values"
    (is (= "N/A" (f/format-size "N/A")))
    (is (= "" (f/format-size "")))
    (is (= "abc" (f/format-size "abc"))))
  (testing "nil returns nil"
    (is (nil? (f/format-size nil)))))
