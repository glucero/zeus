(ns zeus.completion-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.completion :as cmp]))

(deftest complete
  (testing "empty line offers all commands"
    (let [out (cmp/complete "")]
      (is (every? string? out))
      (is (contains? (set out) "select"))
      (is (contains? (set out) "search"))
      (is (contains? (set out) "help"))))
  (testing "command prefix narrows down commands"
    (is (= ["search" "select"] (sort (cmp/complete "se"))))
    (is (= ["help"] (cmp/complete "he"))))
  (testing "after 'select ' offers platforms, types, and 'all'"
    (let [out (set (cmp/complete "select "))]
      (is (contains? out "ps3"))
      (is (contains? out "ps3_games"))
      (is (contains? out "all"))))
  (testing "after 'select ps' narrows platforms/types starting with ps"
    (let [out (set (cmp/complete "select ps"))]
      (is (contains? out "ps3"))
      (is (contains? out "ps3_games"))
      (is (contains? out "psv"))
      (is (not (contains? out "all")))))
  (testing "after 'region ' offers regions plus 'all'"
    (let [out (set (cmp/complete "region "))]
      (is (contains? out "US"))
      (is (contains? out "EU"))
      (is (contains? out "all"))))
  (testing "after 'unregion ' offers the same options as 'region '"
    (is (= (set (cmp/complete "region "))
           (set (cmp/complete "unregion ")))))
  (testing "unrelated context returns empty"
    (is (= [] (cmp/complete "search metal "))))
  (testing "case-insensitive prefix match"
    (is (contains? (set (cmp/complete "SE")) "select"))))
