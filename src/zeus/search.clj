(ns zeus.search
  (:require [clojure.string :as str]
            [zeus.tsv :as tsv]))

(defn- row-title [row]
  (let [n (:name row)]
    (if (str/blank? n)
      (or (:title row) "")
      n)))

(defn row-matches?
  "True when row's region is in the (uppercased) region set AND
   search-term is a case-insensitive substring of its name/title.
   An empty region set matches nothing."
  [row search-term regions]
  (let [region-strs (set (map (comp str/upper-case name) regions))
        row-region (str/upper-case (or (:region row) ""))]
    (boolean
     (and (contains? region-strs row-region)
          (str/includes? (str/lower-case (row-title row))
                         (str/lower-case (or search-term "")))))))

(defn search-content
  "Search across TSVs for rows matching `search-term` and `regions`.
   `tsvs` is a seq of [content-type tsv-file] pairs.
   Matching rows are tagged with :_source = content-type."
  [tsvs search-term regions]
  (vec
   (for [[content-type file] tsvs
         row (tsv/read-tsv file)
         :when (row-matches? row search-term regions)]
     (assoc row :_source content-type))))
