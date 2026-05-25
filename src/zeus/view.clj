(ns zeus.view
  "Render handler events to stdout. The single public entry is `render!`,
   which takes an event tuple `[tag & args]` and looks `tag` up in the
   `renderers` map to find a fn that prints to stdout.

   Handlers in zeus.commands stay pure: they compute the next session and
   a vector of events. zeus.core walks the events and pipes each through
   render!. This keeps logic and presentation independently testable."
  (:require [clojure.string :as str]
            [zeus.colors :as c]
            [zeus.format :as fmt]
            [zeus.platforms :as p]
            [zeus.tsv :as tsv]))

(defn- color-type [t]
  (c/color (c/platform-color (p/platform-from-source t)) (name t)))

(def ^:private level->color
  {:ok :green, :warn :yellow, :err :red, :muted :dim, :info :white})

(defn- joined-types [types]
  (str/join ", " (map color-type (sort types))))

(defn- joined-regions [regions]
  (str/join ", " (sort (map (comp str/upper-case name) regions))))

(defn- render-rule []
  (c/say (c/color :dim (apply str (repeat 28 \-)))))

(defn- render-status [{:keys [selected-types selected-regions force-refresh?]}]
  (println)
  (c/say (c/color :bold "Current Settings"))
  (c/say (c/color :dim (apply str (repeat 17 \-))))
  (if (empty? selected-types)
    (println "  Content:" (c/color :dim "none (use 'select' to add)"))
    (println "  Content:" (joined-types selected-types)))
  (cond
    (= (set p/regions) selected-regions)
    (println "  Regions:" (c/color :green "all"))
    (empty? selected-regions)
    (println "  Regions:" (c/color :red "none"))
    :else
    (println "  Regions:" (c/color :cyan (joined-regions selected-regions))))
  (println "  Refresh:" (if force-refresh?
                          (c/color :yellow "forced")
                          (c/color :green "cached")))
  (println))

(def ^:private help-text
  "
  Commands
  -----------
  select   <type|platform|all>   Add content type(s) to search
  unselect <type|platform|all>   Remove content type(s) from search
  region   <region|all>          Add region(s) to filter (US, EU, JP, ASIA)
  unregion <region|all>          Remove region(s) from filter
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

(defn- render-result-row [index row]
  (let [source (:_source row)
        plat (p/platform-from-source source)
        content-kind (last (str/split (name source) #"_"))]
    (println (format "  %3d. %s | %s | %-40s | %-4s | %9s"
                     (inc index)
                     (c/color (c/platform-color plat) (name plat))
                     content-kind
                     (or (tsv/display-name row) "")
                     (or (:region row) "")
                     (or (fmt/format-size (:file-size row)) "")))))

(defn- render-results [results]
  (c/say (c/color :green (str "found " (count results) " result(s):")))
  (doseq [[index row] (map-indexed vector results)]
    (render-result-row index row)))

(defn- render-license-status [plat item]
  (case plat
    :psv (let [zrif (:zrif item)]
           (println "  zRIF:      "
                    (if (and zrif (not (#{"" "MISSING"} zrif)))
                      (c/color :green "[ok] available")
                      (c/color :red "[!] missing"))))
    (:ps3 :psp) (let [rap (:rap item)]
                  (println "  RAP:       "
                           (cond
                             (= "NOT REQUIRED" rap) (c/color :dim "not required")
                             (and rap (not (#{"" "MISSING"} rap))) (c/color :green "[ok] available")
                             :else (c/color :red "[!] missing"))))
    nil))

(defn- render-item-info [item]
  (let [source (:_source item)
        plat (p/platform-from-source source)
        size-bytes (:file-size item)
        pkg-url (:pkg-direct-link item)]
    (render-rule)
    (c/say (c/color :bold (or (tsv/display-name item) "")))
    (render-rule)
    (println "  Title ID:  " (c/color :cyan (or (:title-id item) "-")))
    (println "  Content ID:" (c/color :dim (or (:content-id item) "-")))
    (println "  Region:    " (or (:region item) "-"))
    (println "  Platform:  " (c/color (c/platform-color plat) (name plat))
             (c/color :dim (str "(" (name source) ")")))
    (when (seq size-bytes)
      (println "  Size:      " (fmt/format-size size-bytes)))
    (println "  PKG:       "
             (if (and pkg-url (not (#{"" "MISSING"} pkg-url)))
               (c/color :green "[ok] available")
               (c/color :red "[!] missing")))
    (render-license-status plat item)
    (render-rule)))

(defn- render-banner []
  (println)
  (c/say (c/color :bold "zeus") "- Interactive Browser")
  (c/say (c/color :dim "type 'help' for commands, 'exit' to quit"))
  (println))

(defn- render-progress [done total]
  (print (format "\r  progress: %.1f%% (%.1f/%.1f MB)"
                 (* 100.0 (/ done (double total)))
                 (/ done (* 1024.0 1024))
                 (/ total (* 1024.0 1024))))
  (flush))

(def renderers
  "Map of event tag -> render fn. Each fn is called with the event's
   trailing args (after the tag). Adding a new event = adding a map entry."
  {:blank             (fn [] (println))
   :rule              render-rule
   :banner            render-banner
   :goodbye           (fn [] (c/say (c/color :dim "goodbye")))
   :say               (fn [level msg]
                        (c/say (c/color (level->color level :white) msg)))
   :status            render-status
   :help              (fn [] (println help-text))
   :cleared           (fn [] (c/say (c/color :yellow "cleared all selections")))
   :refresh-on        (fn [] (c/say (c/color :yellow "force refresh enabled")))
   :refresh-off       (fn [] (c/say (c/color :green "force refresh disabled (using cache)")))
   :refresh-state     (fn [on?] (println "  force refresh is"
                                         (if on? (c/color :yellow "on")
                                             (c/color :green "off"))))
   :types-added       (fn [types] (c/say (c/color :green "added:") (joined-types types)))
   :types-removed     (fn [types] (c/say (c/color :yellow "removed:") (joined-types types)))
   :types-no-change   (fn [] (c/say (c/color :dim "no change")))
   :regions-set       (fn [regions]
                        (println "  regions:"
                                 (c/color :cyan (if (empty? regions) "none"
                                                    (joined-regions regions)))))
   :usage             (fn [msg] (c/say (c/color :dim (str "usage: " msg))))
   :no-types-selected (fn [] (c/say (c/color :yellow "no content types selected")))
   :no-search-results (fn [] (c/say "no search results - run 'search' first"))
   :no-results        (fn [] (c/say (c/color :yellow "no results")))
   :invalid-index     (fn [] (c/say (c/color :red "invalid number")))
   :unknown-command   (fn [cmd] (c/say (c/color :red "unknown command:") cmd
                                       (c/color :dim "(try 'help')")))
   :repl-error        (fn [msg] (c/say (c/color :red "error:") msg))
   :searching         (fn [term] (c/say "searching for" (c/color :cyan term) "..."))
   :results           render-results
   :item-info         render-item-info
   :tsv-warning       (fn [content-type msg]
                        (c/say (c/color :yellow "warning:") "could not load"
                               (color-type content-type) "-" msg))
   :sync-start        (fn [database-count]
                        (c/say (c/color :bold "syncing")
                               (c/color :cyan (str database-count)) "database(s)"))
   :sync-one          (fn [content-type] (c/say (c/color :dim ">") (color-type content-type)))
   :sync-skip         (fn [content-type] (c/say (c/color :yellow "skipping")
                                                (color-type content-type)
                                                (c/color :dim "(no URL configured)")))
   :sync-error        (fn [content-type msg] (c/say (c/color :red "error syncing")
                                                    (name content-type) "-" msg))
   :download-start    (fn [item] (render-rule)
                        (c/say (c/color :bold ">") (tsv/display-name item)))
   :download-pkg      (fn [^java.io.File pkg-file]
                        (c/say (c/color :green "[ok] PKG:") (c/color :dim (.getName pkg-file))))
   :license-file      (fn [^java.io.File lic-file]
                        (c/say (c/color :green "[ok] license:") (c/color :dim (.getName lic-file))))
   :item-error        (fn [msg] (c/say (c/color :red "error:") msg))
   :progress          render-progress
   :progress-done     (fn [] (println))
   :extract-start     (fn [item] (render-rule)
                        (c/say (c/color :bold ">")
                               (or (tsv/display-name item) (tsv/content-id item))))
   :extract-no-pkg    (fn [dir] (c/say (c/color :red "no PKG found in") (str dir)))
   :extract-ok        (fn [^java.io.File out]
                        (c/say (c/color :green "[ok] extracted:")
                               (c/color :dim (.getName out))))
   :extract-fail      (fn [] (c/say (c/color :red "extract failed")))
   :extract-skip      (fn [plat] (c/say (c/color :dim "extract not needed for") (name plat)))
   :fix-renamed       (fn [^java.io.File old-file ^java.io.File target]
                        (c/say (c/color :green "renamed:")
                               (c/color :dim (.getName old-file)) "->"
                               (c/color :cyan (.getName target))))
   :fix-nothing       (fn [] (c/say (c/color :green "[ok]")
                                    "all files already in expected naming format"))
   :fix-summary       (fn [rename-count]
                        (c/say "fixed" (c/color :green (str rename-count)) "file(s)"))
   :license-created   (fn [plat ^java.io.File dir]
                        (c/say (c/color :green "[ok]")
                               (c/color (c/platform-color plat) (name plat))
                               (c/color :dim (.getName dir))))
   :license-nothing   (fn [] (c/say (c/color :green "[ok]")
                                    "all downloads have licenses (or don't need them)"))
   :license-summary   (fn [license-count]
                        (c/say "created" (c/color :green (str license-count)) "license(s)"))})

(defn render!
  "Render one event tuple to stdout. Unknown tags are silently ignored."
  [[tag & args]]
  (when-let [renderer (renderers tag)]
    (apply renderer args)))

(defn render-all!
  "Render every event in `events` in order."
  [events]
  (doseq [event events] (render! event)))
