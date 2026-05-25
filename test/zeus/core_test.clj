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
    (testing "unknown commands return an :unknown-command event"
      (let [out (core/dispatch session "frobnicate" [])]
        (is (= [[:unknown-command "frobnicate"]] (:events out)))
        (is (= session (:session out)))))
    (testing "exit / quit return ::core/exit"
      (is (= ::core/exit (core/dispatch session "exit" [])))
      (is (= ::core/exit (core/dispatch session "quit" []))))
    (testing "known command routes to its handler"
      (let [called (atom nil)]
        (with-redefs [cmd/handle-status (fn [s]
                                          (reset! called :status)
                                          {:session s :events []})]
          (core/dispatch session "status" [])
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
