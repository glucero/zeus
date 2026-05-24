(ns zeus.commands
  (:require [clojure.string :as str]
            [zeus.colors :as c]
            [zeus.platforms :as p]
            [zeus.session :as sess]))

(defn- color-type [t]
  (c/color (c/platform-color (p/platform-from-source t)) (name t)))

(defn- print-types [selected]
  (if (empty? selected)
    (println "  Content:" (c/color :dim "none (use 'select' to add)"))
    (println "  Content:" (str/join ", " (map color-type (sort selected))))))

(defn- print-regions [selected]
  (cond
    (= (set p/regions) selected)
    (println "  Regions:" (c/color :green "all"))

    (empty? selected)
    (println "  Regions:" (c/color :red "none"))

    :else
    (println "  Regions:"
             (c/color :cyan
                      (str/join ", " (sort (map (comp str/upper-case name)
                                                selected)))))))

(defn handle-status
  "Print the session's current selections and refresh mode."
  [{:keys [selected-types selected-regions force-refresh?] :as session}]
  (println)
  (println " " (c/color :bold "Current Settings"))
  (println " " (c/color :dim "─────────────────"))
  (print-types selected-types)
  (print-regions selected-regions)
  (println "  Refresh:"
           (if force-refresh?
             (c/color :yellow "forced")
             (c/color :green "cached")))
  (println)
  session)

(def ^:private help-text
  "
  Commands
  ───────────
  select   <type|platform|all>   Add content type(s) to search
  unselect <type|platform|all>   Remove content type(s) from search
  region   <region|all|clear>    Toggle region filter (US, EU, JP, ASIA)
  search   <term>                Search selected content types
  info     <number>              Show detailed info for a result
  download <numbers>             Download PKG + license
  extract  <numbers|all|missing> Extract ISO from PKG (PSP/PSX only)
  fix      all                   Rename PKG/RAP files to new format
  license  all                   Create missing license files
  sync                           Download/update selected databases
  refresh  on|off                Toggle force-refresh mode
  status                         Show current settings
  clear                          Clear all selections
  help                           Show this help
  exit, quit                     Exit
")

(defn handle-help
  "Print the help text. Returns the session unchanged."
  [session]
  (println help-text)
  session)

(defn handle-refresh
  "Set or display the force-refresh flag.
   args: [] = show, [\"on\"] = enable, [\"off\"] = disable."
  [session args]
  (case (some-> args first str/lower-case)
    "on"  (do (println " " (c/color :yellow "force refresh enabled"))
              (sess/set-refresh session true))
    "off" (do (println " " (c/color :green "force refresh disabled (using cache)"))
              (sess/set-refresh session false))
    (do (println "  force refresh is"
                 (if (:force-refresh? session)
                   (c/color :yellow "on")
                   (c/color :green "off")))
        session)))

(defn handle-clear
  "Drop all selected types and restore all regions."
  [session]
  (println " " (c/color :yellow "cleared all selections"))
  (sess/clear-selections session))

(defn handle-select
  "Add content types named by args to the session selection."
  [session args]
  (let [updated (sess/select-types session args)
        added (sort (clojure.set/difference (:selected-types updated)
                                            (:selected-types session)))]
    (if (seq added)
      (println " " (c/color :green "added:") (str/join ", " (map color-type added)))
      (println " " (c/color :dim "no change")))
    updated))

(defn handle-unselect
  "Remove content types named by args from the session selection."
  [session args]
  (let [updated (sess/unselect-types session args)
        removed (sort (clojure.set/difference (:selected-types session)
                                              (:selected-types updated)))]
    (if (seq removed)
      (println " " (c/color :yellow "removed:") (str/join ", " (map color-type removed)))
      (println " " (c/color :dim "no change")))
    updated))

(defn handle-region
  "Apply region args (toggle / all / clear)."
  [session args]
  (let [updated (sess/set-regions session args)]
    (println "  regions:"
             (c/color :cyan
                      (if (empty? (:selected-regions updated))
                        "none"
                        (str/join ", " (sort (map (comp str/upper-case name)
                                                  (:selected-regions updated)))))))
    updated))
