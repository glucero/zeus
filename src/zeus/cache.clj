(ns zeus.cache)

(def ^:private day-ms (* 24 60 60 1000))

(defn cache-valid?
  "True when mtime (epoch ms) is within expiration-days of now-ms.
   nil mtime or zero expiration is always stale."
  [mtime expiration-days now-ms]
  (boolean
   (and mtime
        (pos? expiration-days)
        (>= mtime (- now-ms (* expiration-days day-ms))))))
