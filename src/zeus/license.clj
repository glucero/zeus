(ns zeus.license)

(def ^:private sentinels #{"MISSING" "NOT REQUIRED" ""})

(defn decode-rap
  "Hex-decode a 32-char RAP string to a 16-byte array.
   Returns nil for nil, sentinel strings, or invalid hex."
  [rap-hex]
  (when (and (string? rap-hex)
             (not (sentinels rap-hex))
             (= 32 (count rap-hex)))
    (try
      (let [out (byte-array 16)]
        (dotimes [i 16]
          (let [b (Integer/parseInt (subs rap-hex (* i 2) (+ 2 (* i 2))) 16)]
            (aset-byte out i (unchecked-byte b))))
        out)
      (catch NumberFormatException _ nil))))
