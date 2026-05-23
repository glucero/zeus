(ns zeus.naming
  (:require [clojure.string :as str]))

(def ^:private invalid-chars #{\< \> \: \" \/ \\ \| \? \*})

(defn sanitize-filename
  "Remove filesystem-invalid characters; trim leading/trailing spaces and dots."
  [name]
  (-> name
      (->> (remove invalid-chars) (apply str))
      (str/replace (re-pattern "^[ .]+") "")
      (str/replace (re-pattern "[ .]+$") "")))
