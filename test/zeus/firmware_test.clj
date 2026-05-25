(ns zeus.firmware-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.firmware :as firmware])
  (:import (java.io ByteArrayInputStream)))

(defn- temp-dir []
  (doto (java.io.File/createTempFile "zeus-fw" "")
    (.delete) (.mkdir) (.deleteOnExit)))

(defn- fake-stream [^String body]
  (fn [_url]
    {:body (ByteArrayInputStream. (.getBytes body))
     :headers {"content-length" (str (.length body))}}))

(def fake-updatelist
  (str "# US\n"
       "Dest=84;CompatibleSystemSoftwareVersion=4.9300-;\n"
       "Dest=84;IncrementalUpdateVersion=00010b72-00010b72;ImageVersion=00010b94;SystemSoftwareVersion=4.9300;CDN=http://example/PATCH.PUP;CDN_Timeout=30;\n"
       "Dest=84;ImageVersion=00010b94;SystemSoftwareVersion=4.9300;CDN=http://example/UP/PS3UPDAT.PUP;CDN_Timeout=30;\n"))

(deftest ps3-pup-url
  (testing "extracts the PS3UPDAT.PUP URL from the update list"
    (with-redefs [firmware/fetch-text (fn [_url] fake-updatelist)]
      (is (= "http://example/UP/PS3UPDAT.PUP" (firmware/ps3-pup-url)))))
  (testing "returns nil when no PS3UPDAT.PUP line is present"
    (with-redefs [firmware/fetch-text (fn [_url] "# Empty\n")]
      (is (nil? (firmware/ps3-pup-url))))))

(deftest download
  (testing "writes body to file and returns the file"
    (let [d (temp-dir)
          f (java.io.File. d "TEST.PUP")]
      (with-redefs [firmware/fetch-stream (fake-stream "FAKE-PUP")]
        (let [out (firmware/download "http://x" f {})]
          (is (= f out))
          (is (= "FAKE-PUP" (slurp out)))))))
  (testing "calls progress-fn after each chunk with [done total]"
    (let [d (temp-dir)
          f (java.io.File. d "TEST.PUP")
          calls (atom [])
          progress (fn [done total] (swap! calls conj [done total]))]
      (with-redefs [firmware/fetch-stream (fake-stream "ABCDEF")]
        (firmware/download "http://x" f {:progress-fn progress :chunk-size 2}))
      (is (seq @calls))
      (is (= [6 6] (last @calls))))))
