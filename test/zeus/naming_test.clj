(ns zeus.naming-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.naming :as n]))

(deftest sanitize-filename
  (testing "strips filesystem-invalid characters"
    (is (= "abc" (n/sanitize-filename "a<b>c")))
    (is (= "abc" (n/sanitize-filename "a:b\"c")))
    (is (= "abc" (n/sanitize-filename "a/b\\c")))
    (is (= "abc" (n/sanitize-filename "a|b?c*"))))
  (testing "trims leading/trailing spaces and dots"
    (is (= "name" (n/sanitize-filename "  name  ")))
    (is (= "name" (n/sanitize-filename "...name...")))
    (is (= "name" (n/sanitize-filename " . name . "))))
  (testing "preserves inner spaces and dots"
    (is (= "Metal Gear Solid 2.0" (n/sanitize-filename "Metal Gear Solid 2.0"))))
  (testing "empty and whitespace"
    (is (= "" (n/sanitize-filename "")))
    (is (= "" (n/sanitize-filename "   ")))
    (is (= "" (n/sanitize-filename "...")))))

(deftest content-base-name
  (testing "combines sanitized name and content id"
    (is (= "Metal Gear Solid [UCUS98632]"
           (n/content-base-name "Metal Gear Solid" "UCUS98632"))))
  (testing "sanitizes invalid chars in name"
    (is (= "AB [UCUS00001]"
           (n/content-base-name "A/B" "UCUS00001"))))
  (testing "falls back to content-id when name is missing"
    (is (= "UCUS98632" (n/content-base-name nil "UCUS98632")))
    (is (= "UCUS98632" (n/content-base-name "" "UCUS98632")))
    (is (= "UCUS98632" (n/content-base-name "   " "UCUS98632")))))

