(ns zeus.view
  "Render handler events to stdout. The single public entry is `render!`,
   which takes an event tuple `[tag & args]` and prints to stdout.

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

(defn- render-say [level msg]
  (c/say (c/color (level->color level :white) msg)))

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

(defn- render-result-row [i row]
  (let [source (:_source row)
        plat (p/platform-from-source source)
        ct (last (str/split (name source) #"_"))]
    (println (format "  %3d. %s | %s | %-40s | %-4s | %9s"
                     (inc i)
                     (c/color (c/platform-color plat) (name plat))
                     ct
                     (or (tsv/display-name row) "")
                     (or (:region row) "")
                     (or (fmt/format-size (:file-size row)) "")))))

(defn- render-results [results]
  (c/say (c/color :green (str "found " (count results) " result(s):")))
  (doseq [[i row] (map-indexed vector results)]
    (render-result-row i row)))

(defn- render-license-status [plat item]
  (case plat
    :psv (let [z (:zrif item)]
           (println "  zRIF:      "
                    (if (and z (not (#{"" "MISSING"} z)))
                      (c/color :green "[ok] available")
                      (c/color :red "[!] missing"))))
    (:ps3 :psp) (let [r (:rap item)]
                  (println "  RAP:       "
                           (cond
                             (= "NOT REQUIRED" r) (c/color :dim "not required")
                             (and r (not (#{"" "MISSING"} r))) (c/color :green "[ok] available")
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

(defn render!
  "Render one event tuple to stdout."
  [[tag & args]]
  (case tag
    :blank             (println)
    :rule              (render-rule)
    :banner            (do (println)
                           (c/say (c/color :bold "zeus")
                                  "- Interactive Browser")
                           (c/say (c/color :dim "type 'help' for commands, 'exit' to quit"))
                           (println))
    :goodbye           (c/say (c/color :dim "goodbye"))
    :say               (apply render-say args)
    :status            (render-status (first args))
    :help              (println help-text)
    :cleared           (c/say (c/color :yellow "cleared all selections"))
    :refresh-on        (c/say (c/color :yellow "force refresh enabled"))
    :refresh-off       (c/say (c/color :green "force refresh disabled (using cache)"))
    :refresh-state     (println "  force refresh is"
                                (if (first args)
                                  (c/color :yellow "on")
                                  (c/color :green "off")))
    :types-added       (c/say (c/color :green "added:") (joined-types (first args)))
    :types-removed     (c/say (c/color :yellow "removed:") (joined-types (first args)))
    :types-no-change   (c/say (c/color :dim "no change"))
    :regions-set       (let [regs (first args)]
                         (println "  regions:"
                                  (c/color :cyan (if (empty? regs) "none"
                                                     (joined-regions regs)))))
    :usage             (c/say (c/color :dim (str "usage: " (first args))))
    :no-types-selected (c/say (c/color :yellow "no content types selected"))
    :no-search-results (c/say "no search results - run 'search' first")
    :no-results        (c/say (c/color :yellow "no results"))
    :invalid-index     (c/say (c/color :red "invalid number"))
    :unknown-command   (c/say (c/color :red "unknown command:") (first args)
                              (c/color :dim "(try 'help')"))
    :repl-error        (c/say (c/color :red "error:") (first args))
    :searching         (c/say "searching for" (c/color :cyan (first args)) "...")
    :results           (render-results (first args))
    :item-info         (render-item-info (first args))
    :tsv-warning       (c/say (c/color :yellow "warning:") "could not load"
                              (color-type (first args)) "-" (second args))
    :sync-start        (c/say (c/color :bold "syncing")
                              (c/color :cyan (str (first args))) "database(s)")
    :sync-one          (c/say (c/color :dim ">") (color-type (first args)))
    :sync-skip         (c/say (c/color :yellow "skipping") (color-type (first args))
                              (c/color :dim "(no URL configured)"))
    :sync-error        (c/say (c/color :red "error syncing")
                              (name (first args)) "-" (second args))
    :download-start    (do (render-rule)
                           (c/say (c/color :bold ">") (tsv/display-name (first args))))
    :download-pkg      (c/say (c/color :green "[ok] PKG:")
                              (c/color :dim (.getName ^java.io.File (first args))))
    :license-file      (c/say (c/color :green "[ok] license:")
                              (c/color :dim (.getName ^java.io.File (first args))))
    :item-error        (c/say (c/color :red "error:") (first args))
    :progress          (let [done (first args) total (second args)]
                         (print (format "\r  progress: %.1f%% (%.1f/%.1f MB)"
                                        (* 100.0 (/ done (double total)))
                                        (/ done (* 1024.0 1024))
                                        (/ total (* 1024.0 1024))))
                         (flush))
    :progress-done     (println)
    :extract-start     (do (render-rule)
                           (c/say (c/color :bold ">")
                                  (or (tsv/display-name (first args))
                                      (tsv/content-id (first args)))))
    :extract-no-pkg    (c/say (c/color :red "no PKG found in") (str (first args)))
    :extract-ok        (c/say (c/color :green "[ok] extracted:")
                              (c/color :dim (.getName ^java.io.File (first args))))
    :extract-fail      (c/say (c/color :red "extract failed"))
    :extract-skip      (c/say (c/color :dim "extract not needed for") (name (first args)))
    :fix-renamed       (c/say (c/color :green "renamed:")
                              (c/color :dim (.getName ^java.io.File (first args))) "->"
                              (c/color :cyan (.getName ^java.io.File (second args))))
    :fix-nothing       (c/say (c/color :green "[ok]") "all files already in expected naming format")
    :fix-summary       (c/say "fixed" (c/color :green (str (first args))) "file(s)")
    :license-created   (c/say (c/color :green "[ok]")
                              (c/color (c/platform-color (first args))
                                       (name (first args)))
                              (c/color :dim (.getName ^java.io.File (second args))))
    :license-nothing   (c/say (c/color :green "[ok]") "all downloads have licenses (or don't need them)")
    :license-summary   (c/say "created" (c/color :green (str (first args))) "license(s)")
    nil))

(defn render-all!
  "Render every event in `events` in order."
  [events]
  (doseq [e events] (render! e)))
