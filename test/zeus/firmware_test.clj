(ns zeus.firmware-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.firmware :as firmware]))

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
