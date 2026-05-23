(ns zeus.cache-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.cache :as c]))

(def day-ms (* 24 60 60 1000))

(deftest cache-valid?
  (testing "fresh file within expiration window"
    (let [now (System/currentTimeMillis)]
      (is (true? (c/cache-valid? (- now (* 2 day-ms)) 7 now)))))
  (testing "stale file past expiration window"
    (let [now (System/currentTimeMillis)]
      (is (false? (c/cache-valid? (- now (* 10 day-ms)) 7 now)))))
  (testing "expiration of 0 days means always stale"
    (let [now (System/currentTimeMillis)]
      (is (false? (c/cache-valid? now 0 now)))))
  (testing "nil mtime (missing file) is stale"
    (is (false? (c/cache-valid? nil 7 (System/currentTimeMillis)))))
  (testing "exact boundary is fresh"
    (let [now (System/currentTimeMillis)]
      (is (true? (c/cache-valid? (- now day-ms) 1 now))))))
