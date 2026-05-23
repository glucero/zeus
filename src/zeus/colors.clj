(ns zeus.colors)

(def ^:private esc "[")
(def reset (str esc "0m"))

(def codes
  {:reset   "0"
   :bold    "1"
   :dim     "2"
   :red     "91"
   :green   "92"
   :yellow  "93"
   :blue    "94"
   :magenta "95"
   :cyan    "96"
   :white   "97"
   :gray    "90"})

(defn color
  "Wrap text in an ANSI color escape. Unknown colors return text unchanged."
  [k text]
  (if-let [code (codes k)]
    (str esc code "m" text reset)
    (str text)))

(def ^:private platform->color
  {:psv :blue
   :ps3 :magenta
   :psp :cyan
   :psx :yellow
   :psm :green})

(defn platform-color [plat]
  (get platform->color plat :white))
