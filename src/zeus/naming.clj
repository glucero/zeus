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

(defn content-base-name
  "Build the file basename (no extension): \"Sanitized Name [CONTENT_ID]\",
   or just the content id when no usable name is given."
  [name content-id]
  (let [clean (some-> name sanitize-filename str/trim)]
    (if (str/blank? clean)
      content-id
      (str clean " [" content-id "]"))))
