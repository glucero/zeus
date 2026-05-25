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
      (is (= "./downloads" (:output-dir c)))
      (is (= "./cache" (:cache-dir c)))
      (is (= 7 (:cache-expiration-days c)))))
  (testing "preserves explicit values, normalizing keys to kebab-case"
    (let [c (cfg/load-config (temp-yaml "output_dir: /tmp/out\ncache_expiration_days: 14\n"))]
      (is (= "/tmp/out" (:output-dir c)))
      (is (= 14 (:cache-expiration-days c)))
      (is (= "./cache" (:cache-dir c)))))
  (testing "preserves catalog-urls map; inner content-type keys stay snake_case"
    (let [c (cfg/load-config (temp-yaml "catalog_urls:\n  psv_games: https://example/psv\n"))]
      (is (= "https://example/psv" (get-in c [:catalog-urls :psv_games])))))
  (testing "session block keys are normalized to kebab-case"
    (let [c (cfg/load-config (temp-yaml "session:\n  selected_types:\n    - psv_games\n  selected_regions:\n    - US\n"))]
      (is (= ["psv_games"] (get-in c [:session :selected-types])))
      (is (= ["US"] (get-in c [:session :selected-regions]))))))

(deftest save-session
  (testing "writes selected types and regions to the file"
    (let [f (temp-yaml "output_dir: /tmp/out\n")]
      (cfg/save-session f #{:ps3_games :psv_dlcs} #{:us :eu})
      (let [c (cfg/load-config f)]
        (is (= ["ps3_games" "psv_dlcs"] (get-in c [:session :selected-types])))
        (is (= ["EU" "US"] (get-in c [:session :selected-regions])))
        (is (= "/tmp/out" (:output-dir c))))))
  (testing "values are sorted for stable diffs"
    (let [f (temp-yaml "")]
      (cfg/save-session f #{:psp_games :ps3_games :psv_games} #{:jp :asia :us})
      (let [c (cfg/load-config f)]
        (is (= ["ps3_games" "psp_games" "psv_games"]
               (get-in c [:session :selected-types])))
        (is (= ["ASIA" "JP" "US"]
               (get-in c [:session :selected-regions]))))))
  (testing "overwrites a previous session block"
    (let [f (temp-yaml "session:\n  selected_types:\n    - old_type\n")]
      (cfg/save-session f #{:psv_games} #{:us})
      (is (= ["psv_games"]
             (get-in (cfg/load-config f) [:session :selected-types])))))
  (testing "file on disk uses snake_case keys (YAML convention)"
    (let [f (temp-yaml "")]
      (cfg/save-session f #{:psv_games :ps3_games} #{:us :eu})
      (let [text (slurp f)]
        (is (re-find #"selected_types:\s*\n\s+- ps3_games" text))
        (is (re-find #"selected_regions:\s*\n\s+- EU" text))))))
