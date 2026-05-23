(ns zeus.config
  (:require [clj-yaml.core :as yaml]))

(def defaults
  {:output_dir "./downloads"
   :cache_dir "./cache"
   :cache_expiration_days 7})

(defn load-config
  "Parse a YAML config file into a map, with defaults applied for missing keys."
  [path]
  (let [parsed (or (yaml/parse-string (slurp path)) {})]
    (merge defaults parsed)))
