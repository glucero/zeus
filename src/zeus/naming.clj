(ns zeus.naming
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [zeus.platforms :as platforms]))

(def ^:private invalid-chars #{\< \> \: \" \/ \\ \| \? \*})

(defn sanitize-filename
  "Remove filesystem-invalid characters; trim leading/trailing spaces and dots."
  [text]
  (-> text
      (->> (remove invalid-chars) (apply str))
      (str/replace (re-pattern "^[ .]+") "")
      (str/replace (re-pattern "[ .]+$") "")))

(defn content-base-name
  "Build the file basename (no extension): \"Sanitized Display [CONTENT_ID]\",
   or just the content id when no usable display name is given."
  [display content-id]
  (let [clean (some-> display sanitize-filename str/trim)]
    (if (str/blank? clean)
      content-id
      (str clean " [" content-id "]"))))

(defn content-dir
  "Build the per-item directory path: output-dir / platform-folder / content-id.
   Unknown source types use the source name as the folder."
  [output-dir source content-id]
  (let [platform (platforms/platform-from-source source)
        folder   (or (platforms/platform-folders platform) (name platform))]
    (io/file output-dir folder content-id)))
