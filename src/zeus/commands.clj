(ns zeus.commands
  "Command handlers. Each handler is pure-ish: takes [session args],
   returns {:session updated-session, :events [event-tuple ...]}.
   The events vector is rendered by zeus.view; the session is the new state.

   For events that must reach the renderer *during* execution (e.g. download
   progress), handlers call `(*emit!* event)`. The dispatcher binds *emit!*
   to the live renderer; tests leave it as a noop."
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [zeus.license :as license]
            [zeus.naming :as naming]
            [zeus.pkg :as pkg]
            [zeus.platforms :as platforms]
            [zeus.search :as search]
            [zeus.session :as sess]
            [zeus.tsv :as tsv]))

(def ^:dynamic *emit!*
  "A function that the renderer can inject so handlers can stream events
   live (e.g. download progress). Defaults to a noop."
  (fn [_event] nil))

(defn- result [session events]
  {:session session :events (vec events)})

(defn handle-status [session]
  (result session [[:status session]]))

(defn handle-help [session]
  (result session [[:help]]))

(defn handle-refresh [session args]
  (case (some-> args first str/lower-case)
    "on"  (result (sess/set-refresh session true)  [[:refresh-on]])
    "off" (result (sess/set-refresh session false) [[:refresh-off]])
    (result session [[:refresh-state (:force-refresh? session)]])))

(defn handle-clear [session]
  (result (sess/clear-selections session) [[:cleared]]))

(defn handle-select [session args]
  (let [updated (sess/select-types session args)
        added (set/difference (:selected-types updated) (:selected-types session))]
    (result updated
            (if (seq added) [[:types-added added]] [[:types-no-change]]))))

(defn handle-unselect [session args]
  (let [updated (sess/unselect-types session args)
        removed (set/difference (:selected-types session) (:selected-types updated))]
    (result updated
            (if (seq removed) [[:types-removed removed]] [[:types-no-change]]))))

(defn handle-region [session args]
  (let [updated (sess/add-regions session args)]
    (result updated [[:regions-set (:selected-regions updated)]])))

(defn handle-unregion [session args]
  (let [updated (sess/remove-regions session args)]
    (result updated [[:regions-set (:selected-regions updated)]])))

(defn- cache-file-for [config content-type]
  (io/file (:cache-dir config) (str (name content-type) ".tsv")))

(defn- sync-one!
  "Download a single TSV. Returns an event describing the outcome."
  [{:keys [config force-refresh?]} content-type]
  (if-let [url (get-in config [:catalog-urls content-type])]
    (try
      (tsv/download-tsv {:url url
                         :cache-file (cache-file-for config content-type)
                         :expiration-days (:cache-expiration-days config)
                         :force? force-refresh?})
      [:sync-one content-type]
      (catch Exception e
        [:sync-error content-type (.getMessage e)]))
    [:sync-skip content-type]))

(defn handle-sync [{:keys [selected-types] :as session}]
  (if (empty? selected-types)
    (result session [[:no-types-selected]])
    (result session
            (into [[:sync-start (count selected-types)]]
                  (map (partial sync-one! session) (sort selected-types))))))

(defn- parse-index [arg max-count]
  (try
    (let [index (dec (Long/parseLong arg))]
      (when (and (>= index 0) (< index max-count)) index))
    (catch NumberFormatException _ nil)))

(defn handle-info [{:keys [last-results] :as session} args]
  (cond
    (empty? args)         (result session [[:usage "info <number>"]])
    (empty? last-results) (result session [[:no-search-results]])
    :else
    (if-let [index (parse-index (first args) (count last-results))]
      (result session [[:item-info (nth last-results index)]])
      (result session [[:invalid-index]]))))

(defn- progress-emitter []
  (fn [done total]
    (*emit!* [:progress done total])))

(defn- download-one!
  "Download PKG + license for one item. Returns a seq of events."
  [{:keys [config]} item]
  (let [content-id   (or (tsv/content-id item) "unknown")
        dir          (naming/content-dir (:output-dir config) (:_source item) content-id)
        _            (.mkdirs ^java.io.File dir)
        pkg-file     (pkg/download-pkg item dir {:progress-fn (progress-emitter)})
        license-file (when pkg-file (license/write-license-file item dir))]
    (cond-> [[:download-start item]]
      pkg-file     (conj [:progress-done] [:download-pkg pkg-file])
      license-file (conj [:license-file license-file]))))

(defn handle-download [{:keys [last-results] :as session} args]
  (cond
    (empty? args)         (result session [[:usage "download <number> [...]"]])
    (empty? last-results) (result session [[:no-search-results]])
    :else
    (result session
            (mapcat (fn [arg]
                      (if-let [index (parse-index arg (count last-results))]
                        (try (download-one! session (nth last-results index))
                             (catch Exception e [[:item-error (.getMessage e)]]))
                        [[:invalid-index]]))
                    args))))

(defn- extract-events
  "Compute events for extracting one indexed item."
  [{:keys [config]} item]
  (let [platform   (platforms/platform-from-source (:_source item))
        content-id (tsv/content-id item)
        dir        (naming/content-dir (:output-dir config) (:_source item) content-id)
        pkg-file   (first (filter #(str/ends-with? (.getName %) ".pkg")
                                  (.listFiles ^java.io.File dir)))]
    (cond
      (nil? pkg-file)
      [[:extract-start item] [:extract-no-pkg dir]]

      (= :psp platform)
      (let [extracted ((requiring-resolve 'zeus.extract/extract-psp) pkg-file dir)]
        [[:extract-start item]
         (if extracted [:extract-ok extracted] [:extract-fail])])

      (= :psx platform)
      (let [extracted ((requiring-resolve 'zeus.extract/extract-psx) pkg-file dir)]
        [[:extract-start item]
         (if extracted [:extract-ok extracted] [:extract-fail])])

      :else
      [[:extract-start item] [:extract-skip platform]])))

(defn handle-extract [{:keys [last-results] :as session} args]
  (cond
    (empty? args)         (result session [[:usage "extract <number> [...]"]])
    (empty? last-results) (result session [[:no-search-results]])
    :else
    (result session
            (mapcat (fn [arg]
                      (if-let [index (parse-index arg (count last-results))]
                        (extract-events session (nth last-results index))
                        [[:invalid-index]]))
                    args))))

(defn- platform-dirs [output-dir]
  (keep (fn [[platform folder]]
          (let [dir (io/file output-dir folder)]
            (when (.isDirectory dir) [platform dir])))
        platforms/platform-folders))

(defn- content-dirs [output-dir]
  (for [[platform platform-dir] (platform-dirs output-dir)
        subdir (.listFiles ^java.io.File platform-dir)
        :when (.isDirectory subdir)]
    [platform subdir]))

(defn- rename-with-base [^java.io.File file new-base]
  (let [extension (subs (.getName file) (.lastIndexOf (.getName file) "."))
        target    (io/file (.getParentFile file) (str new-base extension))]
    (when (and (not= file target) (.renameTo file target))
      target)))

(defn handle-fix [{:keys [config] :as session} args]
  (if (not= ["all"] (mapv str/lower-case args))
    (result session [[:usage "fix all"]])
    (let [lookup (tsv/build-lookup (:cache-dir config))
          renames (doall
                   (for [[_ dir] (content-dirs (:output-dir config))
                         :let [content-id (.getName dir)
                               item       (get lookup content-id)]
                         :when item
                         :let [base (naming/content-base-name
                                     (tsv/display-name item) content-id)]
                         extension ["pkg" "rap"]
                         :let [old-file (io/file dir (str content-id "." extension))]
                         :when (.exists old-file)
                         :let [target (rename-with-base old-file base)]
                         :when target]
                     [old-file target]))]
      (result session
              (if (empty? renames)
                [[:fix-nothing]]
                (conj (mapv (fn [[old-file target]] [:fix-renamed old-file target])
                            renames)
                      [:fix-summary (count renames)]))))))

(defn- has-license? [^java.io.File dir]
  (boolean (or (.exists (io/file dir "work.bin"))
               (some #(str/ends-with? (.getName %) ".rap")
                     (.listFiles dir)))))

(defn handle-license-all [{:keys [config] :as session} args]
  (if (not= ["all"] (mapv str/lower-case args))
    (result session [[:usage "license all"]])
    (let [lookup (tsv/build-lookup (:cache-dir config))
          created (doall
                   (for [[platform dir] (content-dirs (:output-dir config))
                         :let [item (get lookup (.getName dir))]
                         :when (and item (not (has-license? dir)))
                         :let [written (license/write-license-file item dir)]
                         :when written]
                     [platform dir]))]
      (result session
              (if (empty? created)
                [[:license-nothing]]
                (conj (mapv (fn [[platform dir]] [:license-created platform dir])
                            created)
                      [:license-summary (count created)]))))))

(defn- ensure-tsv
  "Make sure the TSV for `content-type` is cached locally.
   Returns {:file f} on success, {:warn [content-type msg]} on failure,
   or nil when no URL is configured."
  [{:keys [config force-refresh?]} content-type]
  (when-let [url (get-in config [:catalog-urls content-type])]
    (try
      {:file (tsv/download-tsv
              {:url url
               :cache-file (cache-file-for config content-type)
               :expiration-days (:cache-expiration-days config)
               :force? force-refresh?})}
      (catch Exception e
        {:warn [content-type (.getMessage e)]}))))

(defn handle-search [{:keys [selected-types selected-regions] :as session} args]
  (cond
    (empty? args)
    (result session [[:usage "search <term>"]])

    (empty? selected-types)
    (result session [[:no-types-selected]])

    :else
    (let [term     (str/join " " args)
          fetches  (map (fn [content-type]
                          [content-type (ensure-tsv session content-type)])
                        (sort selected-types))
          warnings (for [[_ {:keys [warn]}] fetches :when warn]
                     (into [:tsv-warning] warn))
          tsvs     (for [[content-type {:keys [file]}] fetches :when file]
                     [content-type file])
          results  (search/search-content tsvs term selected-regions)
          base-events (concat [[:searching term]] warnings)]
      (if (empty? results)
        (result (sess/set-results session [])
                (concat base-events [[:no-results]]))
        (result (sess/set-results session results)
                (concat base-events [[:results results]]))))))
