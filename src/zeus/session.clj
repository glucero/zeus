(ns zeus.session
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [zeus.platforms :as p]))

(def ^:private valid-types (set p/content-types))
(def ^:private valid-regions (set p/regions))

(defn- restore-types [saved]
  (->> (or saved [])
       (map (comp keyword str/lower-case))
       (filter valid-types)
       set))

(defn- restore-regions [saved]
  (if (nil? saved)
    valid-regions
    (->> saved
         (map (comp keyword str/lower-case))
         (filter valid-regions)
         set)))

(defn new-session
  "Build a fresh session map from a loaded config.
   Saved :session filters are restored; otherwise sensible defaults apply."
  [config]
  {:config config
   :selected-types (restore-types (get-in config [:session :selected_types]))
   :selected-regions (restore-regions (get-in config [:session :selected_regions]))
   :force-refresh? false
   :last-results []
   :page-size 20})

(defn- resolve-types-arg
  "Convert a single select/unselect argument to a set of content types.
   Accepts \"all\", a platform name, or a specific content type; case-insensitive.
   Unknown args return an empty set."
  [arg]
  (let [k (-> arg str/lower-case keyword)]
    (cond
      (= :all k)            (set p/content-types)
      (contains? p/platforms k) (set (get p/platforms k))
      (valid-types k)       #{k}
      :else                 #{})))

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

(defn- apply-region-arg [regions arg]
  (let [k (-> arg str/lower-case keyword)]
    (cond
      (= :all k)         valid-regions
      (= :clear k)       #{}
      (valid-regions k)  (if (regions k) (disj regions k) (conj regions k))
      :else              regions)))

(defn set-regions
  "Apply `args` to the session's region selection.
   \"all\" / \"clear\" reset; specific regions toggle."
  [session args]
  (update session :selected-regions
          (fn [regs] (reduce apply-region-arg regs args))))
