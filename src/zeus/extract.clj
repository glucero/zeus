(ns zeus.extract
  (:require [babashka.process :as process]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn which
  "Locate `binary` on PATH, returning its absolute path or nil."
  [binary]
  (->> (str/split (or (System/getenv "PATH") "") #":")
       (map (fn [path-dir] (io/file path-dir binary)))
       (filter (fn [file] (and (.isFile file) (.canExecute file))))
       first
       (#(some-> % .getAbsolutePath))))

(defn run!
  "Run an external command. Returns true on exit 0.
   `args` is a vector of strings; `opts` may include :dir."
  [args opts]
  (let [{:keys [exit]} (apply process/shell
                              (merge {:dir (some-> (:dir opts) str)
                                      :continue true}
                                     (dissoc opts :dir))
                              args)]
    (zero? exit)))

(defn- find-first [^java.io.File dir name-regex]
  (let [pattern (re-pattern (str "(?i)" name-regex))]
    (first (filter (fn [file] (re-find pattern (.getName file)))
                   (file-seq dir)))))

(defn extract-psp
  "Run pkg2zip on `pkg-file` inside `dir`. Returns the produced ISO/ZIP file."
  [pkg-file dir]
  (when-let [pkg2zip (which "pkg2zip")]
    (when (run! [pkg2zip (str pkg-file)] {:dir dir})
      (or (find-first dir "\\.iso$")
          (find-first dir "\\.zip$")))))

(defn extract-psx
  "Extract a PSX BIN/CUE: pkg2zip -x then psxtract -c on EBOOT.PBP."
  [pkg-file dir]
  (let [pkg2zip (which "pkg2zip")
        psxtract (which "psxtract")]
    (when (and pkg2zip psxtract)
      (when (run! [pkg2zip "-x" (str pkg-file)] {:dir dir})
        (when-let [eboot (find-first dir "^EBOOT\\.PBP$")]
          (when (run! [psxtract "-c" (str eboot)] {:dir dir})
            (find-first dir "\\.bin$")))))))
