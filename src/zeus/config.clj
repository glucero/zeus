(ns zeus.config
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]))

(def defaults
  {:output_dir "./downloads"
   :cache_dir "./cache"
   :cache_expiration_days 7})

(defn load-config
  "Parse a YAML config file into a map, with defaults applied for missing keys."
  [path]
  (let [parsed (or (yaml/parse-string (slurp path)) {})]
    (merge defaults parsed)))

(defn save-session
  "Update the YAML file's :session block with the given selected types and
   regions (both sets of keywords). Other config keys are preserved."
  [path selected-types selected-regions]
  (let [current (or (yaml/parse-string (slurp path)) {})
        types (sort (map name selected-types))
        regions (sort (map (comp str/upper-case name) selected-regions))
        updated (assoc current :session
                       {:selected_types (vec types)
                        :selected_regions (vec regions)})]
    (spit path (yaml/generate-string updated :dumper-options {:flow-style :block}))))
