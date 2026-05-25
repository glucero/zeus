(ns zeus.session-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.session :as sess]))

(defn- empty-session
  "Build a session that starts with no types and no regions selected.
   Useful for tests that want to exercise narrowing from a blank slate."
  []
  (sess/new-session {:session {:selected-types [] :selected-regions []}}))

(deftest new-session
  (testing "fresh config defaults to all types and all regions on"
    (let [s (sess/new-session {})]
      (is (= 11 (count (:selected-types s))))
      (is (= #{:us :eu :jp :asia} (:selected-regions s)))
      (is (false? (:force-refresh? s)))
      (is (= [] (:last-results s)))))
  (testing "restores saved filters from config :session"
    (let [s (sess/new-session
             {:session {:selected-types ["ps3_games" "psv_dlcs"]
                        :selected-regions ["US" "EU"]}})]
      (is (= #{:ps3_games :psv_dlcs} (:selected-types s)))
      (is (= #{:us :eu} (:selected-regions s)))))
  (testing "ignores unknown saved types/regions"
    (let [s (sess/new-session
             {:session {:selected-types ["ps3_games" "bogus"]
                        :selected-regions ["US" "MARS"]}})]
      (is (= #{:ps3_games} (:selected-types s)))
      (is (= #{:us} (:selected-regions s)))))
  (testing "explicit empty saved lists persist as empty (intentional opt-out)"
    (let [s (sess/new-session
             {:session {:selected-types [] :selected-regions []}})]
      (is (= #{} (:selected-types s)))
      (is (= #{} (:selected-regions s))))))

(deftest select-types
  (testing "\"all\" selects every content type"
    (let [s (sess/select-types (empty-session) ["all"])]
      (is (= 11 (count (:selected-types s))))))
  (testing "platform name expands to its content types"
    (let [s (sess/select-types (empty-session) ["ps3"])]
      (is (= #{:ps3_games :ps3_dlcs :ps3_themes :ps3_avatars :ps3_demos}
             (:selected-types s)))))
  (testing "explicit content type is added"
    (let [s (sess/select-types (empty-session) ["psv_dlcs"])]
      (is (= #{:psv_dlcs} (:selected-types s)))))
  (testing "multiple args union"
    (let [s (sess/select-types (empty-session) ["psp" "psv_games"])]
      (is (contains? (:selected-types s) :psp_games))
      (is (contains? (:selected-types s) :psv_games))))
  (testing "unknown args are ignored"
    (let [s (sess/select-types (empty-session) ["nintendo" "ps3"])]
      (is (= 5 (count (:selected-types s))))))
  (testing "case-insensitive args"
    (let [s (sess/select-types (empty-session) ["PS3" "PSV_Games"])]
      (is (contains? (:selected-types s) :ps3_games))
      (is (contains? (:selected-types s) :psv_games))))
  (testing "select is additive against an existing selection"
    (let [s (-> (empty-session)
                (sess/select-types ["ps3_games"])
                (sess/select-types ["psv_games"]))]
      (is (= #{:ps3_games :psv_games} (:selected-types s))))))

(deftest unselect-types
  (testing "\"all\" clears selection"
    (let [s (sess/unselect-types (sess/new-session {}) ["all"])]
      (is (= #{} (:selected-types s)))))
  (testing "platform removes its types from defaults"
    (let [s (sess/unselect-types (sess/new-session {}) ["psm"])]
      (is (not (contains? (:selected-types s) :psm_games)))
      (is (contains? (:selected-types s) :ps3_games))))
  (testing "explicit type removes only itself"
    (let [s (-> (empty-session)
                (sess/select-types ["ps3"])
                (sess/unselect-types ["ps3_dlcs"]))]
      (is (not (contains? (:selected-types s) :ps3_dlcs)))
      (is (contains? (:selected-types s) :ps3_games))))
  (testing "unknown arg is a no-op"
    (let [s (-> (empty-session)
                (sess/select-types ["ps3"])
                (sess/unselect-types ["nintendo"]))]
      (is (= 5 (count (:selected-types s)))))))

(deftest add-regions
  (testing "\"all\" adds every region"
    (let [s (-> (sess/new-session {:session {:selected-regions []}})
                (sess/add-regions ["all"]))]
      (is (= #{:us :eu :jp :asia} (:selected-regions s)))))
  (testing "specific region is added when absent"
    (let [s (-> (sess/new-session {:session {:selected-regions []}})
                (sess/add-regions ["US"]))]
      (is (= #{:us} (:selected-regions s)))))
  (testing "specific region already present is a no-op (no toggle off)"
    (let [s (-> (sess/new-session {:session {:selected-regions ["US" "EU"]}})
                (sess/add-regions ["US"]))]
      (is (= #{:us :eu} (:selected-regions s)))))
  (testing "case-insensitive"
    (let [s (-> (sess/new-session {:session {:selected-regions []}})
                (sess/add-regions ["us" "Eu"]))]
      (is (= #{:us :eu} (:selected-regions s)))))
  (testing "unknown region is ignored"
    (let [s (-> (sess/new-session {:session {:selected-regions ["US"]}})
                (sess/add-regions ["MARS"]))]
      (is (= #{:us} (:selected-regions s))))))

(deftest remove-regions
  (testing "\"all\" empties the set"
    (let [s (sess/remove-regions (sess/new-session {}) ["all"])]
      (is (= #{} (:selected-regions s)))))
  (testing "specific region is removed when present"
    (let [s (sess/remove-regions (sess/new-session {}) ["US"])]
      (is (not (contains? (:selected-regions s) :us)))
      (is (= #{:eu :jp :asia} (:selected-regions s)))))
  (testing "specific region not present is a no-op"
    (let [s (-> (sess/new-session {:session {:selected-regions ["EU"]}})
                (sess/remove-regions ["US"]))]
      (is (= #{:eu} (:selected-regions s))))))

(deftest prompt-str
  (testing "all defaults render as bare 'zeus> '"
    (is (= "zeus> " (sess/prompt-str (sess/new-session {})))))
  (testing "narrowed types only"
    (let [s (sess/new-session {:session {:selected-types ["ps3_games"]}})]
      (is (= "zeus[ps3]> " (sess/prompt-str s)))))
  (testing "platforms are sorted and comma-joined"
    (let [s (sess/new-session
             {:session {:selected-types ["psv_games" "ps3_games"]}})]
      (is (= "zeus[ps3,psv]> " (sess/prompt-str s)))))
  (testing "narrowed regions only render without leading colon"
    (let [s (sess/new-session {:session {:selected-regions ["US" "EU"]}})]
      (is (= "zeus[EU,US]> " (sess/prompt-str s)))))
  (testing "both narrowed render as types:regions"
    (let [s (sess/new-session {:session {:selected-types ["ps3_games"]
                                         :selected-regions ["US" "EU"]}})]
      (is (= "zeus[ps3:EU,US]> " (sess/prompt-str s)))))
  (testing "empty types renders as 'no-type'"
    (let [s (sess/new-session {:session {:selected-types []}})]
      (is (= "zeus[no-type]> " (sess/prompt-str s)))))
  (testing "empty regions renders as 'no-region'"
    (let [s (sess/new-session
             {:session {:selected-types ["ps3_games"] :selected-regions []}})]
      (is (= "zeus[ps3:no-region]> " (sess/prompt-str s))))))

(deftest clear-selections
  (testing "resets both types and regions to defaults"
    (let [s (-> (empty-session)
                (sess/select-types ["ps3"])
                (sess/clear-selections))]
      (is (= 11 (count (:selected-types s))))
      (is (= #{:us :eu :jp :asia} (:selected-regions s))))))

(deftest set-refresh
  (testing "sets the force-refresh? flag"
    (let [s (sess/new-session {})]
      (is (true?  (:force-refresh? (sess/set-refresh s true))))
      (is (false? (:force-refresh? (sess/set-refresh s false)))))))

(deftest set-results
  (testing "stores last search results"
    (let [rs [{:name "A"} {:name "B"}]
          s  (sess/set-results (sess/new-session {}) rs)]
      (is (= rs (:last-results s))))))
