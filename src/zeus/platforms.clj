(ns zeus.platforms)

(def platforms
  {:psv [:psv_games :psv_updates :psv_dlcs]
   :ps3 [:ps3_games :ps3_dlcs :ps3_themes :ps3_avatars :ps3_demos]
   :psp [:psp_games]
   :psx [:psx_games]
   :psm [:psm_games]})

(def content-types
  (vec (mapcat val platforms)))

(def regions [:us :eu :jp :asia])

(def platform-folders
  {:psv "psvita"
   :ps3 "ps3"
   :psp "psp"
   :psx "psx"
   :psm "psm"})

(defn platform-from-source [source]
  (or (some (fn [[plat types]] (when (some #{source} types) plat))
            platforms)
      :unknown))
