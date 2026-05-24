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

(deftest select-types
  (testing "\"all\" selects every content type"
    (let [s (sess/select-types (sess/new-session {}) ["all"])]
      (is (= 11 (count (:selected-types s))))))
  (testing "platform name expands to its content types"
    (let [s (sess/select-types (sess/new-session {}) ["ps3"])]
      (is (= #{:ps3_games :ps3_dlcs :ps3_themes :ps3_avatars :ps3_demos}
             (:selected-types s)))))
  (testing "explicit content type is added"
    (let [s (sess/select-types (sess/new-session {}) ["psv_dlcs"])]
      (is (= #{:psv_dlcs} (:selected-types s)))))
  (testing "multiple args union"
    (let [s (sess/select-types (sess/new-session {}) ["psp" "psv_games"])]
      (is (contains? (:selected-types s) :psp_games))
      (is (contains? (:selected-types s) :psv_games))))
  (testing "unknown args are ignored"
    (let [s (sess/select-types (sess/new-session {}) ["nintendo" "ps3"])]
      (is (= 5 (count (:selected-types s))))))
  (testing "case-insensitive args"
    (let [s (sess/select-types (sess/new-session {}) ["PS3" "PSV_Games"])]
      (is (contains? (:selected-types s) :ps3_games))
      (is (contains? (:selected-types s) :psv_games)))))

(deftest unselect-types
  (testing "\"all\" clears selection"
    (let [s (-> (sess/new-session {})
                (sess/select-types ["all"])
                (sess/unselect-types ["all"]))]
      (is (= #{} (:selected-types s)))))
  (testing "platform removes its types"
    (let [s (-> (sess/new-session {})
                (sess/select-types ["all"])
                (sess/unselect-types ["psm"]))]
      (is (not (contains? (:selected-types s) :psm_games)))
      (is (contains? (:selected-types s) :ps3_games))))
  (testing "explicit type removes only itself"
    (let [s (-> (sess/new-session {})
                (sess/select-types ["ps3"])
                (sess/unselect-types ["ps3_dlcs"]))]
      (is (not (contains? (:selected-types s) :ps3_dlcs)))
      (is (contains? (:selected-types s) :ps3_games))))
  (testing "unknown arg is a no-op"
    (let [s (-> (sess/new-session {})
                (sess/select-types ["ps3"])
                (sess/unselect-types ["nintendo"]))]
      (is (= 5 (count (:selected-types s)))))))

(deftest set-regions
  (testing "\"all\" sets every region"
    (let [s (-> (sess/new-session {:session {:selected_regions []}})
                (sess/set-regions ["all"]))]
      (is (= #{:us :eu :jp :asia} (:selected-regions s)))))
  (testing "\"clear\" empties the set"
    (let [s (sess/set-regions (sess/new-session {}) ["clear"])]
      (is (= #{} (:selected-regions s)))))
  (testing "specific region toggles on when absent"
    (let [s (-> (sess/new-session {:session {:selected_regions []}})
                (sess/set-regions ["US"]))]
      (is (= #{:us} (:selected-regions s)))))
  (testing "specific region toggles off when present"
    (let [s (-> (sess/new-session {:session {:selected_regions ["US" "EU"]}})
                (sess/set-regions ["US"]))]
      (is (= #{:eu} (:selected-regions s)))))
  (testing "case-insensitive"
    (let [s (-> (sess/new-session {:session {:selected_regions []}})
                (sess/set-regions ["us" "Eu"]))]
      (is (= #{:us :eu} (:selected-regions s)))))
  (testing "unknown region is ignored"
    (let [s (-> (sess/new-session {:session {:selected_regions ["US"]}})
                (sess/set-regions ["MARS"]))]
      (is (= #{:us} (:selected-regions s))))))

(deftest prompt-str
  (testing "no types selected, all regions"
    (is (= "zeus[none]> " (sess/prompt-str (sess/new-session {})))))
  (testing "single platform, all regions"
    (let [s (sess/select-types (sess/new-session {}) ["ps3_games"])]
      (is (= "zeus[ps3]> " (sess/prompt-str s)))))
  (testing "platforms are sorted and comma-joined"
    (let [s (sess/select-types (sess/new-session {}) ["psv_games" "ps3_games"])]
      (is (= "zeus[ps3,psv]> " (sess/prompt-str s)))))
  (testing "filtered regions are appended after a colon, uppercased"
    (let [s (-> (sess/new-session {:session {:selected_regions ["US" "EU"]}})
                (sess/select-types ["ps3"]))]
      (is (= "zeus[ps3:EU,US]> " (sess/prompt-str s)))))
  (testing "empty region set renders as 'no-region'"
    (let [s (-> (sess/new-session {:session {:selected_regions []}})
                (sess/select-types ["ps3"]))]
      (is (= "zeus[ps3:no-region]> " (sess/prompt-str s))))))

(deftest clear-selections
  (testing "drops all selected types and restores all regions"
    (let [s (-> (sess/new-session {:session {:selected_regions ["US"]}})
                (sess/select-types ["ps3"])
                (sess/clear-selections))]
      (is (= #{} (:selected-types s)))
      (is (= #{:us :eu :jp :asia} (:selected-regions s))))))

(deftest set-refresh
  (testing "sets the force-refresh? flag"
    (let [s (sess/new-session {})]
      (is (true?  (:force-refresh? (sess/set-refresh s true))))
      (is (false? (:force-refresh? (sess/set-refresh s false)))))))

(deftest set-results
  (testing "stores last search results"
    (let [rs [{"Name" "A"} {"Name" "B"}]
          s  (sess/set-results (sess/new-session {}) rs)]
      (is (= rs (:last-results s))))))

