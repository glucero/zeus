(ns zeus.commands-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [zeus.commands :as cmd]
            [zeus.session :as sess]))

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
