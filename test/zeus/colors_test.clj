(ns zeus.colors-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.colors :as c]))

(def esc "[")
(def reset (str esc "0m"))

(deftest color-wraps-text
  (testing "applies ANSI code and trailing reset"
    (is (= (str esc "91mhello" reset) (c/color :red "hello")))
    (is (= (str esc "92mok" reset) (c/color :green "ok")))
    (is (= (str esc "1mbold" reset) (c/color :bold "bold")))
    (is (= (str esc "2mdim" reset) (c/color :dim "dim"))))
  (testing "coerces non-strings"
    (is (= (str esc "94m42" reset) (c/color :blue 42)))))

(deftest unknown-color-passes-text-through
  (is (= "raw" (c/color :no-such-color "raw"))))

(deftest platform-color
  (testing "each platform has a color"
    (is (= :blue (c/platform-color :psv)))
    (is (= :magenta (c/platform-color :ps3)))
    (is (= :cyan (c/platform-color :psp)))
    (is (= :yellow (c/platform-color :psx)))
    (is (= :green (c/platform-color :psm))))
  (testing "unknown platform falls back to :white"
    (is (= :white (c/platform-color :unknown)))
    (is (= :white (c/platform-color nil)))))

(deftest say
  (testing "two-space indent with newline"
    (is (= "  hello\n" (with-out-str (c/say "hello")))))
  (testing "joins multiple args with single spaces"
    (is (= "  a b c\n" (with-out-str (c/say "a" "b" "c"))))))
