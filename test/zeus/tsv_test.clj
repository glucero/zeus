(ns zeus.tsv-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [zeus.tsv :as tsv]))

(def day-ms (* 24 60 60 1000))

(deftest cache-valid?
  (testing "fresh file within expiration window"
    (let [now (System/currentTimeMillis)]
      (is (true? (tsv/cache-valid? (- now (* 2 day-ms)) 7 now)))))
  (testing "stale file past expiration window"
    (let [now (System/currentTimeMillis)]
      (is (false? (tsv/cache-valid? (- now (* 10 day-ms)) 7 now)))))
  (testing "expiration of 0 days means always stale"
    (let [now (System/currentTimeMillis)]
      (is (false? (tsv/cache-valid? now 0 now)))))
  (testing "nil mtime (missing file) is stale"
    (is (false? (tsv/cache-valid? nil 7 (System/currentTimeMillis)))))
  (testing "exact boundary is fresh"
    (let [now (System/currentTimeMillis)]
      (is (true? (tsv/cache-valid? (- now day-ms) 1 now))))))

(defn- temp-tsv [content]
  (let [f (java.io.File/createTempFile "zeus-test" ".tsv")]
    (.deleteOnExit f)
    (spit f content)
    f))

(deftest read-tsv
  (testing "parses header row into map keys"
    (let [f (temp-tsv "Name\tRegion\tTitle ID\nGame A\tUS\tABC123\nGame B\tEU\tDEF456\n")
          rows (tsv/read-tsv f)]
      (is (= 2 (count rows)))
      (is (= {"Name" "Game A" "Region" "US" "Title ID" "ABC123"}
             (first rows)))
      (is (= "DEF456" (get (second rows) "Title ID")))))
  (testing "empty body yields no rows"
    (let [f (temp-tsv "Name\tRegion\n")]
      (is (= [] (tsv/read-tsv f)))))
  (testing "handles missing trailing values"
    (let [f (temp-tsv "Name\tRegion\tTitle ID\nGame A\tUS\n")
          row (first (tsv/read-tsv f))]
      (is (= "Game A" (get row "Name")))
      (is (= "US" (get row "Region")))
      (is (nil? (get row "Title ID"))))))
