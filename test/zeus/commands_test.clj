(ns zeus.commands-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [zeus.commands :as cmd]
            [zeus.session :as sess]
            [zeus.tsv :as tsv]))

(defn- temp-dir []
  (doto (java.io.File/createTempFile "zeus-cmd" "")
    (.delete) (.mkdir) (.deleteOnExit)))

(defn- silenced [f & args]
  (binding [*out* (java.io.StringWriter.)] (apply f args)))

(deftest handle-status
  (testing "returns session unchanged"
    (let [s (sess/new-session {})]
      (is (= s (silenced cmd/handle-status s)))))
  (testing "prints current settings"
    (let [out (with-out-str (cmd/handle-status (sess/new-session {})))]
      (is (str/includes? out "Content"))
      (is (str/includes? out "Regions"))
      (is (str/includes? out "Refresh")))))

(deftest handle-help
  (testing "prints command list including 'search' and 'download'"
    (let [out (with-out-str (cmd/handle-help (sess/new-session {})))]
      (is (str/includes? out "search"))
      (is (str/includes? out "download"))
      (is (str/includes? out "extract")))))

(deftest handle-refresh
  (testing "'on' enables force refresh"
    (let [s (silenced cmd/handle-refresh (sess/new-session {}) ["on"])]
      (is (true? (:force-refresh? s)))))
  (testing "'off' disables force refresh"
    (let [s (-> (sess/new-session {})
                (sess/set-refresh true)
                (->> (#(silenced cmd/handle-refresh % ["off"]))))]
      (is (false? (:force-refresh? s)))))
  (testing "no args prints current state and leaves session alone"
    (let [s (sess/set-refresh (sess/new-session {}) true)
          out (with-out-str (cmd/handle-refresh s []))]
      (is (str/includes? (str/lower-case out) "on")))))

(deftest handle-clear
  (testing "drops selected types and restores all regions"
    (let [s (-> (sess/new-session {:session {:selected_regions ["US"]}})
                (sess/select-types ["ps3"])
                (->> (silenced cmd/handle-clear)))]
      (is (= #{} (:selected-types s)))
      (is (= #{:us :eu :jp :asia} (:selected-regions s))))))

(deftest handle-select
  (testing "delegates to session/select-types"
    (let [s (silenced cmd/handle-select (sess/new-session {}) ["ps3"])]
      (is (= 5 (count (:selected-types s)))))))

(deftest handle-unselect
  (testing "delegates to session/unselect-types"
    (let [s (-> (sess/new-session {})
                (sess/select-types ["all"])
                (->> (#(silenced cmd/handle-unselect % ["psm"]))))]
      (is (not (contains? (:selected-types s) :psm_games))))))

(deftest handle-region
  (testing "delegates to session/set-regions"
    (let [s (-> (sess/new-session {:session {:selected_regions []}})
                (->> (#(silenced cmd/handle-region % ["us" "eu"]))))]
      (is (= #{:us :eu} (:selected-regions s))))))

(deftest handle-sync
  (testing "downloads a TSV for each selected type"
    (let [dir (temp-dir)
          config {:cache_dir (str dir)
                  :cache_expiration_days 7
                  :catalog_urls {:ps3_games "http://x/ps3"
                             :psv_games "http://x/psv"}}
          session (-> (sess/new-session config)
                      (sess/select-types ["ps3_games" "psv_games"]))
          fetched (atom #{})]
      (with-redefs [tsv/fetch-bytes (fn [url]
                                      (swap! fetched conj url)
                                      (.getBytes "x"))]
        (silenced cmd/handle-sync session))
      (is (= #{"http://x/ps3" "http://x/psv"} @fetched))
      (is (.exists (io/file dir "ps3_games.tsv")))
      (is (.exists (io/file dir "psv_games.tsv")))))
  (testing "skips types with no URL in config"
    (let [dir (temp-dir)
          session (-> (sess/new-session
                       {:cache_dir (str dir) :cache_expiration_days 7
                        :catalog_urls {}})
                      (sess/select-types ["ps3_games"]))
          fetched (atom 0)]
      (with-redefs [tsv/fetch-bytes (fn [_] (swap! fetched inc) (byte-array 0))]
        (silenced cmd/handle-sync session))
      (is (zero? @fetched)))))

(deftest handle-info
  (let [session (-> (sess/new-session {})
                    (sess/set-results
                     [{:_source :ps3_games
                       "Name" "Test Game" "Title ID" "BLUS00001"
                       "Content ID" "NPUB12345" "Region" "US"
                       "File Size" "1073741824"
                       "PKG direct link" "http://x"
                       "RAP" "0123456789abcdef0123456789abcdef"}]))]
    (testing "prints details for valid index"
      (let [out (with-out-str (cmd/handle-info session ["1"]))]
        (is (str/includes? out "Test Game"))
        (is (str/includes? out "NPUB12345"))
        (is (str/includes? out "ps3"))
        (is (str/includes? out "1.00 GB"))))
    (testing "out-of-range index prints error"
      (let [out (with-out-str (cmd/handle-info session ["5"]))]
        (is (str/includes? (str/lower-case out) "invalid"))))
    (testing "no args prints usage"
      (let [out (with-out-str (cmd/handle-info session []))]
        (is (str/includes? (str/lower-case out) "usage"))))
    (testing "empty results prints helpful message"
      (let [out (with-out-str (cmd/handle-info (sess/new-session {}) ["1"]))]
        (is (str/includes? (str/lower-case out) "search"))))))

(deftest handle-search
  (let [dir (temp-dir)
        _ (spit (io/file dir "ps3_games.tsv")
                "Name\tRegion\tContent ID\tFile Size\nMetal Gear\tUS\tNPUB1\t1073741824\nPersona\tJP\tNPJB2\t2147483648\n")
        _ (spit (io/file dir "psv_games.tsv")
                "Name\tRegion\tContent ID\tFile Size\nUncharted\tUS\tPCSE1\t1500000000\n")
        config {:cache_dir (str dir)
                :cache_expiration_days 7
                :catalog_urls {:ps3_games "http://x/ps3"
                           :psv_games "http://x/psv"}}
        session (-> (sess/new-session config)
                    (sess/select-types ["ps3_games" "psv_games"]))]
    (testing "stores matching results in session"
      (let [s (silenced cmd/handle-search session ["metal"])]
        (is (= 1 (count (:last-results s))))
        (is (= "Metal Gear" (get (first (:last-results s)) "Name")))))
    (testing "results respect the active region filter"
      (let [filtered (assoc session :selected-regions #{:jp})
            s (silenced cmd/handle-search filtered [""])]
        (is (= ["Persona"] (map #(get % "Name") (:last-results s))))))
    (testing "no selected types prints a warning and leaves results untouched"
      (let [empty-sess (sess/new-session config)
            s (silenced cmd/handle-search empty-sess ["x"])]
        (is (= [] (:last-results s)))))
    (testing "empty search args asks for a term"
      (let [s (silenced cmd/handle-search session [])]
        (is (= [] (:last-results s)))))))
