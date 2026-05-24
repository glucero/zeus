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

(deftest header->key
  (testing "lowercases and dasherizes"
    (is (= :content-id (tsv/header->key "Content ID")))
    (is (= :pkg-direct-link (tsv/header->key "PKG direct link")))
    (is (= :name (tsv/header->key "Name")))
    (is (= :zrif (tsv/header->key "zRIF")))))

(deftest read-tsv
  (testing "parses header row into kebab-case keyword keys"
    (let [f (temp-tsv "Name\tRegion\tTitle ID\nGame A\tUS\tABC123\nGame B\tEU\tDEF456\n")
          rows (tsv/read-tsv f)]
      (is (= 2 (count rows)))
      (is (= {:name "Game A" :region "US" :title-id "ABC123"}
             (first rows)))
      (is (= "DEF456" (:title-id (second rows))))))
  (testing "empty body yields no rows"
    (let [f (temp-tsv "Name\tRegion\n")]
      (is (= [] (tsv/read-tsv f)))))
  (testing "handles missing trailing values"
    (let [f (temp-tsv "Name\tRegion\tTitle ID\nGame A\tUS\n")
          row (first (tsv/read-tsv f))]
      (is (= "Game A" (:name row)))
      (is (= "US" (:region row)))
      (is (nil? (:title-id row))))))

(defn- temp-dir []
  (doto (java.io.File/createTempFile "zeus-cache" "")
    (.delete) (.mkdir) (.deleteOnExit)))

(deftest download-tsv
  (testing "downloads and writes when cache is missing"
    (let [dir (temp-dir)
          cache-file (io/file dir "x.tsv")
          calls (atom 0)]
      (with-redefs [tsv/fetch-bytes (fn [_] (swap! calls inc)
                                      (.getBytes "header\nrow1\n"))]
        (let [out (tsv/download-tsv {:url "http://x"
                                     :cache-file cache-file
                                     :expiration-days 7
                                     :force? false})]
          (is (= cache-file out))
          (is (= 1 @calls))
          (is (= "header\nrow1\n" (slurp out)))))))
  (testing "skips fetch when cache is fresh"
    (let [dir (temp-dir)
          cache-file (io/file dir "x.tsv")
          _ (spit cache-file "cached body")
          calls (atom 0)]
      (with-redefs [tsv/fetch-bytes (fn [_] (swap! calls inc) (byte-array 0))]
        (tsv/download-tsv {:url "http://x" :cache-file cache-file
                           :expiration-days 7 :force? false})
        (is (zero? @calls))
        (is (= "cached body" (slurp cache-file))))))
  (testing "force? bypasses fresh cache"
    (let [dir (temp-dir)
          cache-file (io/file dir "x.tsv")
          _ (spit cache-file "stale")
          calls (atom 0)]
      (with-redefs [tsv/fetch-bytes (fn [_] (swap! calls inc) (.getBytes "fresh"))]
        (tsv/download-tsv {:url "http://x" :cache-file cache-file
                           :expiration-days 7 :force? true})
        (is (= 1 @calls))
        (is (= "fresh" (slurp cache-file)))))))

(deftest build-lookup
  (testing "merges rows across cached TSVs by Content ID"
    (let [dir (temp-dir)]
      (spit (io/file dir "ps3_games.tsv")
            "Content ID\tName\nNPUB1\tGame A\nNPUB2\tGame B\n")
      (spit (io/file dir "psv_games.tsv")
            "Content ID\tName\nPCSE1\tVita Game\n")
      (let [lookup (tsv/build-lookup dir)]
        (is (= 3 (count lookup)))
        (is (= "Game A" (get-in lookup ["NPUB1" :name])))
        (is (= :ps3_games (get-in lookup ["NPUB1" :_source])))
        (is (= :psv_games (get-in lookup ["PCSE1" :_source]))))))
  (testing "falls back to Title ID when Content ID is missing"
    (let [dir (temp-dir)]
      (spit (io/file dir "psp_games.tsv")
            "Title ID\tName\nUCUS001\tOld Game\n")
      (is (= "Old Game"
             (get-in (tsv/build-lookup dir) ["UCUS001" :name])))))
  (testing "missing TSVs are skipped silently"
    (is (= {} (tsv/build-lookup (temp-dir))))))
