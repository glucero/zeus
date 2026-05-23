(ns zeus.search-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.search :as s]))

(defn- temp-tsv [content]
  (let [f (java.io.File/createTempFile "zeus-search" ".tsv")]
    (.deleteOnExit f)
    (spit f content)
    f))

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

(deftest search-content
  (let [ps3 (temp-tsv "Name\tRegion\nMetal Gear\tUS\nKillzone\tEU\n")
        psv (temp-tsv "Name\tRegion\nMetal Slug\tJP\nUncharted\tUS\n")]
    (testing "returns matching rows tagged with :_source"
      (let [results (s/search-content [[:ps3_games ps3]
                                       [:psv_games psv]]
                                      "metal" all-regions)]
        (is (= 2 (count results)))
        (is (= #{:ps3_games :psv_games} (set (map :_source results))))
        (is (every? #(re-find #"(?i)metal" (get % "Name")) results))))
    (testing "region filter applies"
      (let [results (s/search-content [[:ps3_games ps3] [:psv_games psv]]
                                      "" #{:eu})]
        (is (= ["Killzone"] (map #(get % "Name") results)))))
    (testing "empty input yields empty result"
      (is (empty? (s/search-content [] "x" all-regions))))))
