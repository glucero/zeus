(ns zeus.completion
  (:require [clojure.string :as str]
            [zeus.platforms :as platforms]))

(def commands
  ["select" "unselect" "region" "unregion" "search" "download" "extract"
   "fix" "license" "info" "refresh" "status" "sync" "clear"
   "help" "exit" "quit"])

(def ^:private select-options
  (concat ["all"]
          (map name (keys platforms/platforms))
          (map name platforms/content-types)))

(def ^:private region-options
  (concat ["all"]
          (map (comp str/upper-case name) platforms/regions)))

(defn- options-at [position cmd]
  (case position
    0 commands
    1 (case cmd
        ("select" "unselect")   select-options
        ("region" "unregion")   region-options
        [])
    []))

(defn complete
  "Return the list of completions for the partial word at the end of `line`."
  [line]
  (let [line (or line "")
        tokens (->> (str/split line #"\s+") (remove str/blank?) vec)
        trailing? (and (seq line)
                       (Character/isWhitespace ^char (.charAt line (dec (count line)))))
        committed (if trailing? tokens (vec (butlast tokens)))
        prefix    (if trailing? "" (or (peek tokens) ""))
        prefix-lc (str/lower-case prefix)]
    (vec (filter (fn [option] (str/starts-with? (str/lower-case option) prefix-lc))
                 (options-at (count committed) (first committed))))))
