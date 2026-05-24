(ns zeus.pkg-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [zeus.pkg :as pkg])
  (:import (java.io ByteArrayInputStream)))

(defn- temp-dir []
  (doto (java.io.File/createTempFile "zeus-pkg" "")
    (.delete) (.mkdir) (.deleteOnExit)))

(defn- fake-stream [^String body]
  (fn [_url _opts]
    {:body (ByteArrayInputStream. (.getBytes body))
     :headers {"content-length" (str (.length body))}}))

(def item {:_source :ps3_games
           "Content ID" "NPUB12345"
           "Name" "Test Game"
           "PKG direct link" "http://example/x.pkg"})

(deftest download-pkg
  (testing "downloads to '<base>.pkg' and returns the file"
    (let [d (temp-dir)]
      (with-redefs [pkg/fetch-stream (fake-stream "FAKE-PKG-BYTES")]
        (let [out (pkg/download-pkg item d {})]
          (is (= "Test Game [NPUB12345].pkg" (.getName out)))
          (is (= "FAKE-PKG-BYTES" (slurp out)))))))
  (testing "skips download when a *.pkg already exists"
    (let [d (temp-dir)
          existing (io/file d "Anything.pkg")
          _ (spit existing "old")
          called (atom 0)]
      (with-redefs [pkg/fetch-stream (fn [& _] (swap! called inc)
                                       {:body (ByteArrayInputStream. (byte-array 0))
                                        :headers {}})]
        (let [out (pkg/download-pkg item d {})]
          (is (= existing out))
          (is (zero? @called))))))
  (testing "returns nil when URL is missing or MISSING"
    (is (nil? (pkg/download-pkg (dissoc item "PKG direct link") (temp-dir) {})))
    (is (nil? (pkg/download-pkg (assoc item "PKG direct link" "MISSING")
                                (temp-dir) {}))))
  (testing "invokes progress callback with downloaded/total"
    (let [d (temp-dir)
          observations (atom [])]
      (with-redefs [pkg/fetch-stream (fake-stream "0123456789")]
        (pkg/download-pkg item d {:progress-fn (fn [done total]
                                                 (swap! observations conj [done total]))
                                  :chunk-size 4}))
      (is (= [[4 10] [8 10] [10 10]] @observations))))
  (testing "writes to a .partial then renames on completion"
    (let [d (temp-dir)]
      (with-redefs [pkg/fetch-stream (fake-stream "DONE")]
        (pkg/download-pkg item d {})
        (is (empty? (filter #(re-find #"\.partial$" (.getName %))
                            (.listFiles d))))))))
