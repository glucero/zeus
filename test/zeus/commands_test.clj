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

(defn- ev-tags [outcome]
  (mapv first (:events outcome)))

(defn- has-tag? [outcome tag]
  (some #{tag} (ev-tags outcome)))

(deftest handle-status
  (testing "returns session unchanged"
    (let [s (sess/new-session {})]
      (is (= s (:session (cmd/handle-status s))))))
  (testing "emits :status event"
    (let [s (sess/new-session {})
          out (cmd/handle-status s)]
      (is (= [[:status s]] (:events out))))))

(deftest handle-help
  (testing "emits :help event"
    (is (= [[:help]] (:events (cmd/handle-help (sess/new-session {})))))))

(deftest handle-refresh
  (testing "'on' enables flag, emits :refresh-on"
    (let [out (cmd/handle-refresh (sess/new-session {}) ["on"])]
      (is (true? (:force-refresh? (:session out))))
      (is (= [[:refresh-on]] (:events out)))))
  (testing "'off' disables flag, emits :refresh-off"
    (let [out (cmd/handle-refresh (sess/set-refresh (sess/new-session {}) true)
                                  ["off"])]
      (is (false? (:force-refresh? (:session out))))
      (is (= [[:refresh-off]] (:events out)))))
  (testing "no args shows state via :refresh-state event"
    (let [s (sess/set-refresh (sess/new-session {}) true)
          out (cmd/handle-refresh s [])]
      (is (= [[:refresh-state true]] (:events out))))))

(deftest handle-clear
  (testing "drops selections; emits :cleared"
    (let [out (-> (sess/new-session {:session {:selected-regions ["US"]}})
                  (sess/select-types ["ps3"])
                  (cmd/handle-clear))]
      (is (= #{} (:selected-types (:session out))))
      (is (= #{:us :eu :jp :asia} (:selected-regions (:session out))))
      (is (= [[:cleared]] (:events out))))))

(deftest handle-select
  (testing "adds types; emits :types-added"
    (let [out (cmd/handle-select (sess/new-session {}) ["ps3"])]
      (is (= 5 (count (:selected-types (:session out)))))
      (is (has-tag? out :types-added))))
  (testing "no-op emits :types-no-change"
    (let [s (sess/select-types (sess/new-session {}) ["ps3"])
          out (cmd/handle-select s ["nintendo"])]
      (is (= [[:types-no-change]] (:events out))))))

(deftest handle-unselect
  (testing "removes types; emits :types-removed"
    (let [s (sess/select-types (sess/new-session {}) ["all"])
          out (cmd/handle-unselect s ["psm"])]
      (is (not (contains? (:selected-types (:session out)) :psm_games)))
      (is (has-tag? out :types-removed)))))

(deftest handle-region
  (testing "updates regions; emits :regions-set with new set"
    (let [out (-> (sess/new-session {:session {:selected-regions []}})
                  (cmd/handle-region ["us" "eu"]))]
      (is (= #{:us :eu} (:selected-regions (:session out))))
      (is (= [[:regions-set #{:us :eu}]] (:events out))))))

(deftest handle-sync
  (testing "downloads a TSV for each selected type"
    (let [dir (temp-dir)
          config {:cache-dir (str dir)
                  :cache-expiration-days 7
                  :catalog-urls {:ps3_games "http://x/ps3"
                             :psv_games "http://x/psv"}}
          session (-> (sess/new-session config)
                      (sess/select-types ["ps3_games" "psv_games"]))
          fetched (atom #{})]
      (with-redefs [tsv/fetch-bytes (fn [url] (swap! fetched conj url)
                                      (.getBytes "x"))]
        (cmd/handle-sync session))
      (is (= #{"http://x/ps3" "http://x/psv"} @fetched))
      (is (.exists (io/file dir "ps3_games.tsv")))))
  (testing "no selected types emits :no-types-selected"
    (let [out (cmd/handle-sync (sess/new-session {}))]
      (is (= [[:no-types-selected]] (:events out))))))

(deftest handle-info
  (let [item {:_source :ps3_games
              :name "Test Game" :title-id "BLUS00001"
              :content-id "NPUB12345" :region "US"
              :file-size "1073741824"
              :pkg-direct-link "http://x"
              :rap "0123456789abcdef0123456789abcdef"}
        session (-> (sess/new-session {}) (sess/set-results [item]))]
    (testing "emits :item-info for valid index"
      (is (= [[:item-info item]] (:events (cmd/handle-info session ["1"])))))
    (testing "out-of-range emits :invalid-index"
      (is (= [[:invalid-index]] (:events (cmd/handle-info session ["5"])))))
    (testing "no args emits :usage"
      (is (has-tag? (cmd/handle-info session []) :usage)))
    (testing "empty results emits :no-search-results"
      (is (= [[:no-search-results]]
             (:events (cmd/handle-info (sess/new-session {}) ["1"])))))))

(deftest handle-download
  (let [out-dir (temp-dir)
        config {:output-dir (str out-dir)}
        item {:_source :ps3_games
              :name "Test" :content-id "NPUB1"
              :pkg-direct-link "http://x/x.pkg"
              :rap "0123456789abcdef0123456789abcdef"}
        session (-> (sess/new-session config) (sess/set-results [item]))]
    (testing "writes PKG + license; emits start/pkg/license events"
      (with-redefs [pkg/fetch-stream
                    (fn [_ _]
                      {:body (ByteArrayInputStream. (.getBytes "pkgbytes"))
                       :headers {"content-length" "8"}})]
        (let [out (cmd/handle-download session ["1"])
              content-dir (io/file out-dir "ps3" "NPUB1")
              files (set (map #(.getName %) (.listFiles content-dir)))]
          (is (contains? files "Test [NPUB1].pkg"))
          (is (contains? files "Test [NPUB1].rap"))
          (is (has-tag? out :download-pkg))
          (is (has-tag? out :license-file)))))
    (testing "usage when no args"
      (is (has-tag? (cmd/handle-download session []) :usage)))
    (testing "no results yet"
      (is (= [[:no-search-results]]
             (:events (cmd/handle-download (sess/new-session config) ["1"])))))))

(deftest handle-extract
  (let [out-dir (temp-dir)
        item-dir (doto (io/file out-dir "psp" "UCUS001") .mkdirs)
        _ (spit (io/file item-dir "game.pkg") "x")
        item {:_source :psp_games :name "PSP Game" :content-id "UCUS001"}
        session (-> (sess/new-session {:output-dir (str out-dir)})
                    (sess/set-results [item]))]
    (testing "psp item dispatches to extract-psp; emits :extract-ok"
      (with-redefs [ex/extract-psp (fn [_ dir]
                                     (spit (io/file dir "GAME.ISO") "iso")
                                     (io/file dir "GAME.ISO"))]
        (is (has-tag? (cmd/handle-extract session ["1"]) :extract-ok))))
    (testing "psx item dispatches to extract-psx"
      (let [psx-dir (doto (io/file out-dir "psx" "SCUS001") .mkdirs)
            _ (spit (io/file psx-dir "x.pkg") "x")
            psx-item {:_source :psx_games :content-id "SCUS001"}
            sess  (-> (sess/new-session {:output-dir (str out-dir)})
                      (sess/set-results [psx-item]))
            called (atom 0)]
        (with-redefs [ex/extract-psx (fn [_ _] (swap! called inc) nil)]
          (is (has-tag? (cmd/handle-extract sess ["1"]) :extract-fail)))
        (is (= 1 @called))))
    (testing "usage when no args"
      (is (has-tag? (cmd/handle-extract session []) :usage)))))

(defn- write-fixture-tsv [dir filename content]
  (spit (doto (io/file dir filename) (-> (.getParentFile) (.mkdirs))) content))

(deftest handle-fix
  (let [out-dir (temp-dir)
        cache-dir (temp-dir)
        _ (write-fixture-tsv cache-dir "ps3_games.tsv"
                             "Content ID\tName\nNPUB1\tCool Game\n")
        config {:output-dir (str out-dir) :cache-dir (str cache-dir)}
        session (sess/new-session config)
        item-dir (doto (io/file out-dir "ps3" "NPUB1") .mkdirs)
        _ (spit (io/file item-dir "NPUB1.pkg") "x")
        _ (spit (io/file item-dir "NPUB1.rap") "y")]
    (testing "renames files; emits :fix-renamed + :fix-summary"
      (let [out (cmd/handle-fix session ["all"])
            names (set (map #(.getName %) (.listFiles item-dir)))]
        (is (contains? names "Cool Game [NPUB1].pkg"))
        (is (contains? names "Cool Game [NPUB1].rap"))
        (is (= 2 (count (filter #{:fix-renamed} (ev-tags out)))))
        (is (has-tag? out :fix-summary))))
    (testing "rerun emits :fix-nothing"
      (is (= [[:fix-nothing]] (:events (cmd/handle-fix session ["all"])))))
    (testing "usage error"
      (is (has-tag? (cmd/handle-fix session []) :usage)))))

(deftest handle-license-all
  (let [out-dir (temp-dir)
        cache-dir (temp-dir)
        valid-rap "0123456789abcdef0123456789abcdef"
        _ (write-fixture-tsv cache-dir "ps3_games.tsv"
                             (str "Content ID\tName\tRAP\nNPUB1\tCool Game\t"
                                  valid-rap "\n"))
        config {:output-dir (str out-dir) :cache-dir (str cache-dir)}
        session (sess/new-session config)
        item-dir (doto (io/file out-dir "ps3" "NPUB1") .mkdirs)]
    (testing "writes missing licenses; emits :license-created + summary"
      (let [out (cmd/handle-license-all session ["all"])
            names (set (map #(.getName %) (.listFiles item-dir)))]
        (is (contains? names "Cool Game [NPUB1].rap"))
        (is (has-tag? out :license-created))
        (is (has-tag? out :license-summary))))
    (testing "skips already-licensed; emits :license-nothing"
      (is (= [[:license-nothing]]
             (:events (cmd/handle-license-all session ["all"])))))
    (testing "usage error"
      (is (has-tag? (cmd/handle-license-all session []) :usage)))))

(deftest handle-search
  (let [dir (temp-dir)
        _ (spit (io/file dir "ps3_games.tsv")
                "Name\tRegion\tContent ID\tFile Size\nMetal Gear\tUS\tNPUB1\t1073741824\nPersona\tJP\tNPJB2\t2147483648\n")
        _ (spit (io/file dir "psv_games.tsv")
                "Name\tRegion\tContent ID\tFile Size\nUncharted\tUS\tPCSE1\t1500000000\n")
        config {:cache-dir (str dir)
                :cache-expiration-days 7
                :catalog-urls {:ps3_games "http://x/ps3"
                           :psv_games "http://x/psv"}}
        session (-> (sess/new-session config)
                    (sess/select-types ["ps3_games" "psv_games"]))]
    (testing "stores matching results in session and emits :results"
      (let [out (cmd/handle-search session ["metal"])]
        (is (= 1 (count (:last-results (:session out)))))
        (is (= "Metal Gear"
               (:name (first (:last-results (:session out))))))
        (is (has-tag? out :results))))
    (testing "respects active region filter"
      (let [filtered (assoc session :selected-regions #{:jp})
            out (cmd/handle-search filtered [""])]
        (is (= ["Persona"] (map :name (:last-results (:session out)))))))
    (testing "no selected types"
      (is (= [[:no-types-selected]]
             (:events (cmd/handle-search (sess/new-session config) ["x"])))))
    (testing "empty search args yields :usage"
      (is (has-tag? (cmd/handle-search session []) :usage)))))
