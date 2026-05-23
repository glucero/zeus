(ns zeus.search
  (:require [clojure.string :as str]))

(defn- row-title [row]
  (let [n (get row "Name")]
    (if (str/blank? n)
      (or (get row "Title") "")
      n)))

(defn row-matches?
  "True when row's region is in the (uppercased) region set AND
   search-term is a case-insensitive substring of its Name/Title.
   An empty region set matches nothing."
  [row search-term regions]
  (let [region-strs (set (map (comp str/upper-case name) regions))
        row-region (str/upper-case (or (get row "Region") ""))]
    (boolean
     (and (contains? region-strs row-region)
          (str/includes? (str/lower-case (row-title row))
                         (str/lower-case (or search-term "")))))))
