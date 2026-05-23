(ns zeus.session-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.session :as sess]))

(deftest new-session
  (testing "defaults to all regions and no selected types"
    (let [s (sess/new-session {})]
      (is (= #{} (:selected-types s)))
      (is (= #{:us :eu :jp :asia} (:selected-regions s)))
      (is (false? (:force-refresh? s)))
      (is (= [] (:last-results s)))))
  (testing "restores saved filters from config :session"
    (let [s (sess/new-session
             {:session {:selected_types ["ps3_games" "psv_dlcs"]
                        :selected_regions ["US" "EU"]}})]
      (is (= #{:ps3_games :psv_dlcs} (:selected-types s)))
      (is (= #{:us :eu} (:selected-regions s)))))
  (testing "ignores unknown saved types/regions"
    (let [s (sess/new-session
             {:session {:selected_types ["ps3_games" "bogus"]
                        :selected_regions ["US" "MARS"]}})]
      (is (= #{:ps3_games} (:selected-types s)))
      (is (= #{:us} (:selected-regions s)))))
  (testing "explicit empty saved regions persists empty (matches nothing)"
    (let [s (sess/new-session
             {:session {:selected_regions []}})]
      (is (= #{} (:selected-regions s))))))
