(ns zeus.commands
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [zeus.colors :as c]
            [zeus.format :as fmt]
            [zeus.license :as license]
            [zeus.naming :as naming]
            [zeus.pkg :as pkg]
            [zeus.platforms :as p]
            [zeus.search :as search]
            [zeus.session :as sess]
            [zeus.tsv :as tsv]))

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

(defn- cache-file-for [config content-type]
  (io/file (:cache_dir config) (str (name content-type) ".tsv")))

(defn- sync-one [{:keys [config force-refresh?]} content-type]
  (if-let [url (get-in config [:catalog_urls content-type])]
    (do (println " " (c/color :dim "⬇") (color-type content-type))
        (tsv/download-tsv {:url url
                           :cache-file (cache-file-for config content-type)
                           :expiration-days (:cache_expiration_days config)
                           :force? force-refresh?}))
    (println " " (c/color :yellow "skipping") (color-type content-type)
             (c/color :dim "(no URL configured)"))))

(defn handle-sync
  "Download/refresh the TSVs for every selected content type."
  [{:keys [selected-types] :as session}]
  (if (empty? selected-types)
    (println " " (c/color :yellow "no content types selected"))
    (do (println " " (c/color :bold "syncing")
                 (c/color :cyan (str (count selected-types)))
                 "database(s)")
        (doseq [ct (sort selected-types)]
          (try (sync-one session ct)
               (catch Exception e
                 (println " " (c/color :red "error syncing")
                          (name ct) "—" (.getMessage e)))))))
  session)

(defn- parse-index [arg max-n]
  (try
    (let [i (dec (Long/parseLong arg))]
      (when (and (>= i 0) (< i max-n)) i))
    (catch NumberFormatException _ nil)))

(defn- print-license-status [plat item]
  (case plat
    :psv (let [z (get item "zRIF")]
           (println "  zRIF:      "
                    (if (and z (not (#{"" "MISSING"} z)))
                      (c/color :green "✓ available")
                      (c/color :red "✗ missing"))))
    (:ps3 :psp) (let [r (get item "RAP")]
                  (println "  RAP:       "
                           (cond
                             (= "NOT REQUIRED" r) (c/color :dim "not required")
                             (and r (not (#{"" "MISSING"} r))) (c/color :green "✓ available")
                             :else (c/color :red "✗ missing"))))
    nil))

(defn- progress-printer []
  (fn [done total]
    (print (format "\r  progress: %.1f%% (%.1f/%.1f MB)"
                   (* 100.0 (/ done (double total)))
                   (/ done (* 1024.0 1024))
                   (/ total (* 1024.0 1024))))
    (flush)))

(defn- download-one [{:keys [config]} item]
  (let [content-id (or (get item "Content ID") (get item "Title ID") "unknown")
        dir (naming/content-dir (:output_dir config) (:_source item) content-id)]
    (.mkdirs ^java.io.File dir)
    (println (c/color :dim "  ────────────────────────────"))
    (println " " (c/color :bold "⬇") (or (get item "Name") (get item "Title")))
    (when-let [pkg-file (pkg/download-pkg item dir {:progress-fn (progress-printer)})]
      (println)
      (println " " (c/color :green "✓ PKG:") (c/color :dim (.getName pkg-file))))
    (when-let [lic (license/write-license-file item dir)]
      (println " " (c/color :green "✓ license:") (c/color :dim (.getName lic))))))

(defn handle-download
  "Download PKG + license for each indexed item in last-results."
  [{:keys [last-results] :as session} args]
  (cond
    (empty? args)         (println "  usage: download <number> [number2 ...]")
    (empty? last-results) (println "  no search results — run 'search' first")
    :else
    (let [indices (keep (fn [a] (parse-index a (count last-results))) args)]
      (doseq [i indices]
        (try (download-one session (nth last-results i))
             (catch Exception e
               (println " " (c/color :red "error:") (.getMessage e)))))))
  session)

(defn handle-info
  "Print full details for last-results[n-1]."
  [{:keys [last-results] :as session} args]
  (cond
    (empty? args)        (println "  usage: info <number>")
    (empty? last-results) (println "  no search results — run 'search' first")
    :else
    (if-let [idx (parse-index (first args) (count last-results))]
      (let [item (nth last-results idx)
            source (:_source item)
            plat (p/platform-from-source source)
            size-bytes (get item "File Size")
            pkg-url (get item "PKG direct link")]
        (println " " (c/color :dim "────────────────────────────"))
        (println " " (c/color :bold (or (get item "Name") (get item "Title") "")))
        (println " " (c/color :dim "────────────────────────────"))
        (println "  Title ID:  " (c/color :cyan (or (get item "Title ID") "—")))
        (println "  Content ID:" (c/color :dim (or (get item "Content ID") "—")))
        (println "  Region:    " (or (get item "Region") "—"))
        (println "  Platform:  " (c/color (c/platform-color plat) (name plat))
                 (c/color :dim (str "(" (name source) ")")))
        (when (seq size-bytes)
          (println "  Size:      " (fmt/format-size size-bytes)))
        (println "  PKG:       "
                 (if (and pkg-url (not (#{"" "MISSING"} pkg-url)))
                   (c/color :green "✓ available")
                   (c/color :red "✗ missing")))
        (print-license-status plat item)
        (println " " (c/color :dim "────────────────────────────")))
      (println " " (c/color :red "invalid number"))))
  session)

(defn- ensure-tsv
  "Ensure the TSV for `content-type` is on disk and return its file, or nil
   when no URL is configured / download fails."
  [{:keys [config force-refresh?]} content-type]
  (when-let [url (get-in config [:catalog_urls content-type])]
    (try
      (tsv/download-tsv {:url url
                         :cache-file (cache-file-for config content-type)
                         :expiration-days (:cache_expiration_days config)
                         :force? force-refresh?})
      (catch Exception _ nil))))

(defn- print-result-row [i row]
  (let [source (:_source row)
        plat (p/platform-from-source source)
        ct (last (str/split (name source) #"_"))]
    (println (format "  %3d. %s │ %s │ %-40s │ %-4s │ %9s"
                     (inc i)
                     (c/color (c/platform-color plat) (name plat))
                     ct
                     (or (get row "Name") (get row "Title") "")
                     (or (get row "Region") "")
                     (or (fmt/format-size (get row "File Size")) "")))))

(defn handle-search
  "Search across the selected content types' TSVs for `term`.
   Stores matches in session :last-results and prints a numbered list."
  [{:keys [selected-types selected-regions] :as session} args]
  (cond
    (empty? args)
    (do (println "  usage: search <term>") session)

    (empty? selected-types)
    (do (println " " (c/color :yellow "no content types selected — use 'select' first"))
        session)

    :else
    (let [term (str/join " " args)
          _ (println "  searching for" (c/color :cyan term) "...")
          tsvs (keep (fn [ct] (when-let [f (ensure-tsv session ct)] [ct f]))
                     (sort selected-types))
          results (search/search-content tsvs term selected-regions)]
      (if (empty? results)
        (do (println " " (c/color :yellow "no results")) (sess/set-results session []))
        (do (println " " (c/color :green (str "found " (count results) " result(s):")))
            (doseq [[i row] (map-indexed vector results)]
              (print-result-row i row))
            (sess/set-results session results))))))
