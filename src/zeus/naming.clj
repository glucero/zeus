(ns zeus.naming
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [zeus.platforms :as p]))

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

(defn content-dir
  "Build the per-item directory path: output-dir / platform-folder / content-id.
   Unknown source types use the source name as the folder."
  [output-dir source content-id]
  (let [plat (p/platform-from-source source)
        folder (or (p/platform-folders plat) (name plat))]
    (io/file output-dir folder content-id)))
