(ns zeus.search-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.search :as s]))

(def all-regions #{:us :eu :jp :asia})

(deftest row-matches?
  (testing "search term is matched case-insensitively in Name"
    (is (true? (s/row-matches? {"Name" "Metal Gear Solid" "Region" "US"}
                               "metal" all-regions)))
    (is (true? (s/row-matches? {"Name" "Metal Gear Solid" "Region" "US"}
                               "GEAR" all-regions))))
  (testing "falls back to Title when Name is missing or blank"
    (is (true? (s/row-matches? {"Title" "Tetris" "Region" "JP"}
                               "tet" all-regions))))
  (testing "search term not found"
    (is (false? (s/row-matches? {"Name" "Metal Gear" "Region" "US"}
                                "zelda" all-regions))))
  (testing "region filter"
    (is (true? (s/row-matches? {"Name" "X" "Region" "US"} "" #{:us})))
    (is (false? (s/row-matches? {"Name" "X" "Region" "US"} "" #{:eu})))
    (is (true? (s/row-matches? {"Name" "X" "Region" "us"} "" #{:us}))))
  (testing "empty region set matches nothing"
    (is (false? (s/row-matches? {"Name" "X" "Region" "US"} "" #{}))))
  (testing "empty search term matches any name"
    (is (true? (s/row-matches? {"Name" "Anything" "Region" "US"}
                               "" all-regions)))))
