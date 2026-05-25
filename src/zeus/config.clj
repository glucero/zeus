(ns zeus.config
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]))

(def defaults
  {:output-dir "./downloads"
   :cache-dir "./cache"
   :cache-expiration-days 7})

(defn- kebabify [k]
  (keyword (str/replace (name k) "_" "-")))

(defn- snakeify [k]
  (keyword (str/replace (name k) "-" "_")))

(defn- normalize-loaded
  "Convert top-level YAML keys (snake_case from the file) to kebab-case
   keywords, and do the same for keys inside :session. Inner :catalog-urls
   keys (content-type names) are left as-is, since they double as the
   keywords used throughout the project."
  [parsed]
  (let [top (update-keys parsed kebabify)]
    (cond-> top
      (:session top) (update :session update-keys kebabify))))

(defn- denormalize-for-save
  "Convert top-level + :session keys back to snake_case for the YAML file."
  [m]
  (let [top (update-keys m snakeify)]
    (cond-> top
      (:session top) (update :session update-keys snakeify))))

(defn load-config
  "Parse a YAML config file into a map, with defaults applied for missing
   keys. Top-level and session keys are normalized to kebab-case keywords."
  [path]
  (let [parsed (or (yaml/parse-string (slurp path)) {})]
    (merge defaults (normalize-loaded parsed))))

(defn save-session
  "Update the YAML file's :session block with the given selected types and
   regions (both sets of keywords). Other config keys are preserved. The
   file is written with snake_case keys (YAML convention)."
  [path selected-types selected-regions]
  (let [current (or (yaml/parse-string (slurp path)) {})
        types (sort (map name selected-types))
        regions (sort (map (comp str/upper-case name) selected-regions))
        updated (-> (normalize-loaded current)
                    (assoc :session
                           {:selected-types (vec types)
                            :selected-regions (vec regions)}))]
    (spit path (yaml/generate-string (denormalize-for-save updated)
                                     :dumper-options {:flow-style :block}))))
