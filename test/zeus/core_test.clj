(ns zeus.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.commands :as cmd]
            [zeus.core :as core]
            [zeus.session :as sess]))

(deftest parse-args
  (testing "defaults config path to 'config.yaml'"
    (is (= "config.yaml" (:config-path (core/parse-args [])))))
  (testing "--config sets config path"
    (is (= "/tmp/x.yaml"
           (:config-path (core/parse-args ["--config" "/tmp/x.yaml"]))))))

(deftest dispatch
  (let [session (sess/new-session {})]
    (testing "unknown commands print an error and return session"
      (let [out (with-out-str (core/dispatch session "frobnicate" []))]
        (is (re-find #"(?i)unknown" out))))
    (testing "exit / quit return ::core/exit"
      (is (= ::core/exit (binding [*out* (java.io.StringWriter.)]
                           (core/dispatch session "exit" []))))
      (is (= ::core/exit (binding [*out* (java.io.StringWriter.)]
                           (core/dispatch session "quit" [])))))
    (testing "known command routes to its handler"
      (let [called (atom nil)]
        (with-redefs [cmd/handle-status (fn [s] (reset! called :status) s)]
          (binding [*out* (java.io.StringWriter.)]
            (core/dispatch session "status" []))
          (is (= :status @called)))))))

(deftest mutating?
  (testing "commands that change persistent state are flagged"
    (is (true? (core/mutating? "select")))
    (is (true? (core/mutating? "unselect")))
    (is (true? (core/mutating? "region")))
    (is (true? (core/mutating? "clear"))))
  (testing "read-only commands are not flagged"
    (is (false? (core/mutating? "search")))
    (is (false? (core/mutating? "status")))
    (is (false? (core/mutating? "help")))
    (is (false? (core/mutating? "download")))))
