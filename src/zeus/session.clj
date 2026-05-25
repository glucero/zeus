(ns zeus.session
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [zeus.platforms :as platforms]))

(def ^:private valid-types (set platforms/content-types))
(def ^:private valid-regions (set platforms/regions))

(defn- valid-keywords
  "Lowercase + keywordize each item in xs, keep only those in valid-set."
  [valid-set xs]
  (into #{}
        (comp (map (comp keyword str/lower-case))
              (filter valid-set))
        xs))

(defn- restore-or-default
  "When `saved` is nil (key absent from config), return the full default set
   so the user starts with nothing filtered out. When `saved` is an explicit
   empty list, return an empty set (intentional opt-out). Otherwise filter
   `saved` down to the valid members."
  [valid-set saved]
  (if (nil? saved)
    valid-set
    (valid-keywords valid-set saved)))

(defn- restore-types [saved]
  (restore-or-default valid-types saved))

(defn- restore-regions [saved]
  (restore-or-default valid-regions saved))

(defn new-session
  "Build a fresh session map from a loaded config.
   Saved :session filters are restored; otherwise sensible defaults apply."
  [config]
  {:config config
   :selected-types (restore-types (get-in config [:session :selected-types]))
   :selected-regions (restore-regions (get-in config [:session :selected-regions]))
   :force-refresh? false
   :last-results []
   :page-size 20})

(defn- resolve-types-arg
  "Convert a single select/unselect argument to a set of content types.
   Accepts \"all\", a platform name, or a specific content type; case-insensitive.
   Unknown args return an empty set."
  [arg]
  (let [kw (-> arg str/lower-case keyword)]
    (cond
      (= :all kw)                        (set platforms/content-types)
      (contains? platforms/platforms kw) (set (get platforms/platforms kw))
      (valid-types kw)                   #{kw}
      :else                              #{})))

(defn- types-from-args [args]
  (reduce set/union #{} (map resolve-types-arg args)))

(defn select-types
  "Add the content types named by `args` to the session's selection."
  [session args]
  (update session :selected-types set/union (types-from-args args)))

(defn unselect-types
  "Remove the content types named by `args` from the session's selection."
  [session args]
  (update session :selected-types set/difference (types-from-args args)))

(defn- resolve-regions-arg
  "Convert a single region argument to a set of region keywords.
   Accepts \"all\" or a specific region (case-insensitive).
   Unknown args return an empty set."
  [arg]
  (let [kw (-> arg str/lower-case keyword)]
    (cond
      (= :all kw)       valid-regions
      (valid-regions kw) #{kw}
      :else             #{})))

(defn- regions-from-args [args]
  (reduce set/union #{} (map resolve-regions-arg args)))

(defn add-regions
  "Add the regions named by `args` to the session's region set.
   `args` accepts specific regions or \"all\"; case-insensitive."
  [session args]
  (update session :selected-regions set/union (regions-from-args args)))

(defn remove-regions
  "Remove the regions named by `args` from the session's region set.
   `args` accepts specific regions or \"all\"; case-insensitive."
  [session args]
  (update session :selected-regions set/difference (regions-from-args args)))

(defn- types-part [selected-types]
  (cond
    (= valid-types selected-types) nil
    (empty? selected-types)        "no-type"
    :else                          (->> selected-types
                                        (map platforms/platform-from-source)
                                        (filter some?)
                                        distinct
                                        (map name)
                                        sort
                                        (str/join ","))))

(defn- regions-part [selected-regions]
  (cond
    (= valid-regions selected-regions) nil
    (empty? selected-regions)          "no-region"
    :else                              (->> selected-regions
                                            (map (comp str/upper-case name))
                                            sort
                                            (str/join ","))))

(defn prompt-str
  "Render the interactive prompt summarizing the current selection.
   When both types and regions are at full default, the brackets are
   omitted entirely (`zeus> `). When one side is at default and the other
   is narrowed, only the narrowed side appears (`zeus[ps3]> `, `zeus[US]> `).
   When both are narrowed, both appear separated by a colon."
  [{:keys [selected-types selected-regions]}]
  (let [types-str   (types-part selected-types)
        regions-str (regions-part selected-regions)]
    (cond
      (and (nil? types-str) (nil? regions-str))
      "zeus> "

      (and types-str regions-str)
      (str "zeus[" types-str ":" regions-str "]> ")

      :else
      (str "zeus[" (or types-str regions-str) "]> "))))

(defn clear-selections
  "Reset both selections back to the defaults (all types, all regions)."
  [session]
  (assoc session
         :selected-types valid-types
         :selected-regions valid-regions))

(defn set-refresh
  "Toggle the force-refresh? flag."
  [session on?]
  (assoc session :force-refresh? (boolean on?)))

(defn set-results
  "Store the latest search results so commands like `info` and `download`
   can refer to them by index."
  [session results]
  (assoc session :last-results (vec results)))
