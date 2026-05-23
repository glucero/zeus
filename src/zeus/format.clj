(ns zeus.format)

(def ^:private bytes-per-gb (* 1024 1024 1024))

(defn format-size
  "Convert a byte-count string to \"X.XX GB\".
   Returns the input unchanged for nil, empty, or non-numeric values."
  [size]
  (if (nil? size)
    nil
    (try
      (let [n (Long/parseLong size)]
        (format "%.2f GB" (/ (double n) bytes-per-gb)))
      (catch NumberFormatException _ size))))
