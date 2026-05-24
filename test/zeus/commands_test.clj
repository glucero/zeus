(ns zeus.commands-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [zeus.commands :as cmd]
            [zeus.extract :as ex]
            [zeus.pkg :as pkg]
            [zeus.session :as sess]
            [zeus.tsv :as tsv])
  (:import (java.io ByteArrayInputStream)))

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

(deftest handle-extract
  (let [out-dir (temp-dir)
        item-dir (doto (io/file out-dir "psp" "UCUS001") .mkdirs)
        _ (spit (io/file item-dir "game.pkg") "x")
        item {:_source :psp_games :name "PSP Game"
              :content-id "UCUS001"}
        session (-> (sess/new-session {:output_dir (str out-dir)})
                    (sess/set-results [item]))
        calls (atom [])]
    (testing "indexed item dispatches to extract-psp"
      (with-redefs [ex/extract-psp (fn [pkg dir]
                                     (swap! calls conj [:psp (str pkg) (str dir)])
                                     (io/file dir "GAME.ISO"))]
        (silenced cmd/handle-extract session ["1"]))
      (is (= 1 (count @calls)))
      (is (= :psp (first (first @calls)))))
    (testing "psx item dispatches to extract-psx"
      (let [psx-dir (doto (io/file out-dir "psx" "SCUS001") .mkdirs)
            _ (spit (io/file psx-dir "x.pkg") "x")
            psx-item {:_source :psx_games :content-id "SCUS001"}
            session (-> (sess/new-session {:output_dir (str out-dir)})
                        (sess/set-results [psx-item]))
            calls (atom [])]
        (with-redefs [ex/extract-psx (fn [pkg dir]
                                       (swap! calls conj [:psx])
                                       (io/file dir "OUT.bin"))]
          (silenced cmd/handle-extract session ["1"]))
        (is (= [[:psx]] @calls))))
    (testing "usage error when no args"
      (let [out (with-out-str (cmd/handle-extract session []))]
        (is (str/includes? (str/lower-case out) "usage"))))))

(defn- write-fixture-tsv [dir filename content]
  (spit (doto (io/file dir filename) (-> (.getParentFile) (.mkdirs))) content))

(deftest handle-license-all
  (let [out-dir (temp-dir)
        cache-dir (temp-dir)
        valid-rap "0123456789abcdef0123456789abcdef"
        _ (write-fixture-tsv cache-dir "ps3_games.tsv"
                             (str "Content ID\tName\tRAP\nNPUB1\tCool Game\t"
                                  valid-rap "\n"))
        config {:output_dir (str out-dir) :cache_dir (str cache-dir)}
        session (sess/new-session config)
        item-dir (doto (io/file out-dir "ps3" "NPUB1") .mkdirs)]
    (testing "writes missing license files"
      (silenced cmd/handle-license-all session ["all"])
      (let [names (set (map #(.getName %) (.listFiles item-dir)))]
        (is (contains? names "Cool Game [NPUB1].rap"))))
    (testing "skips items that already have a license"
      (silenced cmd/handle-license-all session ["all"])
      (let [raps (filter #(.endsWith (.getName %) ".rap")
                         (.listFiles item-dir))]
        (is (= 1 (count raps)))))
    (testing "usage error without 'all'"
      (let [out (with-out-str (cmd/handle-license-all session []))]
        (is (str/includes? (str/lower-case out) "usage"))))))

(deftest handle-fix
  (let [out-dir (temp-dir)
        cache-dir (temp-dir)
        _ (write-fixture-tsv cache-dir "ps3_games.tsv"
                             "Content ID\tName\nNPUB1\tCool Game\n")
        config {:output_dir (str out-dir) :cache_dir (str cache-dir)}
        session (sess/new-session config)
        item-dir (doto (io/file out-dir "ps3" "NPUB1") .mkdirs)
        _ (spit (io/file item-dir "NPUB1.pkg") "x")
        _ (spit (io/file item-dir "NPUB1.rap") "y")]
    (testing "renames CONTENT_ID-named files to the basename format"
      (silenced cmd/handle-fix session ["all"])
      (let [names (set (map #(.getName %) (.listFiles item-dir)))]
        (is (contains? names "Cool Game [NPUB1].pkg"))
        (is (contains? names "Cool Game [NPUB1].rap"))
        (is (not (contains? names "NPUB1.pkg")))))
    (testing "rerun is a no-op (already renamed)"
      (silenced cmd/handle-fix session ["all"])
      (is (= 2 (count (.listFiles item-dir)))))
    (testing "usage error for missing 'all'"
      (let [out (with-out-str (cmd/handle-fix session []))]
        (is (str/includes? (str/lower-case out) "usage"))))))

(deftest handle-download
  (let [out-dir (temp-dir)
        config {:output_dir (str out-dir)}
        item {:_source :ps3_games
              :name "Test" :content-id "NPUB1"
              :pkg-direct-link "http://x/x.pkg"
              :rap "0123456789abcdef0123456789abcdef"}
        session (-> (sess/new-session config)
                    (sess/set-results [item]))]
    (testing "writes PKG and license for indexed item"
      (with-redefs [pkg/fetch-stream
                    (fn [_ _]
                      {:body (ByteArrayInputStream. (.getBytes "pkgbytes"))
                       :headers {"content-length" "8"}})]
        (silenced cmd/handle-download session ["1"]))
      (let [content-dir (io/file out-dir "ps3" "NPUB1")
            files (set (map #(.getName %) (.listFiles content-dir)))]
        (is (contains? files "Test [NPUB1].pkg"))
        (is (contains? files "Test [NPUB1].rap"))))
    (testing "usage error when no args"
      (let [out (with-out-str (cmd/handle-download session []))]
        (is (str/includes? (str/lower-case out) "usage"))))
    (testing "no results yet"
      (let [out (with-out-str (cmd/handle-download (sess/new-session config)
                                                   ["1"]))]
        (is (str/includes? (str/lower-case out) "search"))))))

(deftest handle-info
  (let [session (-> (sess/new-session {})
                    (sess/set-results
                     [{:_source :ps3_games
                       :name "Test Game" :title-id "BLUS00001"
                       :content-id "NPUB12345" :region "US"
                       :file-size "1073741824"
                       :pkg-direct-link "http://x"
                       :rap "0123456789abcdef0123456789abcdef"}]))]
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
        (is (= "Metal Gear" (:name (first (:last-results s)))))))
    (testing "results respect the active region filter"
      (let [filtered (assoc session :selected-regions #{:jp})
            s (silenced cmd/handle-search filtered [""])]
        (is (= ["Persona"] (map :name (:last-results s))))))
    (testing "no selected types prints a warning and leaves results untouched"
      (let [empty-sess (sess/new-session config)
            s (silenced cmd/handle-search empty-sess ["x"])]
        (is (= [] (:last-results s)))))
    (testing "empty search args asks for a term"
      (let [s (silenced cmd/handle-search session [])]
        (is (= [] (:last-results s)))))))
