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
