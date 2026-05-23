(ns zeus.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.config :as cfg]))

(defn- temp-yaml [content]
  (let [f (java.io.File/createTempFile "zeus-cfg" ".yaml")]
    (.deleteOnExit f)
    (spit f content)
    f))

(deftest load-config
  (testing "applies defaults when keys are missing"
    (let [c (cfg/load-config (temp-yaml ""))]
      (is (= "./downloads" (:output_dir c)))
      (is (= "./cache" (:cache_dir c)))
      (is (= 7 (:cache_expiration_days c)))))
  (testing "preserves explicit values"
    (let [c (cfg/load-config (temp-yaml "output_dir: /tmp/out\ncache_expiration_days: 14\n"))]
      (is (= "/tmp/out" (:output_dir c)))
      (is (= 14 (:cache_expiration_days c)))
      (is (= "./cache" (:cache_dir c)))))
  (testing "preserves catalog_urls map"
    (let [c (cfg/load-config (temp-yaml "catalog_urls:\n  psv_games: https://example/psv\n"))]
      (is (= "https://example/psv" (get-in c [:catalog_urls :psv_games])))))
  (testing "preserves session block"
    (let [c (cfg/load-config (temp-yaml "session:\n  selected_types:\n    - psv_games\n  selected_regions:\n    - US\n"))]
      (is (= ["psv_games"] (get-in c [:session :selected_types])))
      (is (= ["US"] (get-in c [:session :selected_regions]))))))

(deftest save-session
  (testing "writes selected types and regions to the file"
    (let [f (temp-yaml "output_dir: /tmp/out\n")]
      (cfg/save-session f #{:ps3_games :psv_dlcs} #{:us :eu})
      (let [c (cfg/load-config f)]
        (is (= ["ps3_games" "psv_dlcs"] (get-in c [:session :selected_types])))
        (is (= ["EU" "US"] (get-in c [:session :selected_regions])))
        (is (= "/tmp/out" (:output_dir c))))))
  (testing "values are sorted for stable diffs"
    (let [f (temp-yaml "")]
      (cfg/save-session f #{:psp_games :ps3_games :psv_games} #{:jp :asia :us})
      (let [c (cfg/load-config f)]
        (is (= ["ps3_games" "psp_games" "psv_games"]
               (get-in c [:session :selected_types])))
        (is (= ["ASIA" "JP" "US"]
               (get-in c [:session :selected_regions]))))))
  (testing "overwrites a previous session block"
    (let [f (temp-yaml "session:\n  selected_types:\n    - old_type\n")]
      (cfg/save-session f #{:psv_games} #{:us})
      (is (= ["psv_games"]
             (get-in (cfg/load-config f) [:session :selected_types]))))))
