(ns zeus.session
  (:require [clojure.string :as str]
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
