(ns zeus.core
  (:require [clojure.string :as str]
            [zeus.colors :as c]
            [zeus.commands :as cmd]
            [zeus.config :as cfg]
            [zeus.session :as sess]))

(defn parse-args
  "Parse top-level CLI args. Recognizes --config <path>."
  [argv]
  (loop [opts {:config-path "config.yaml"} args argv]
    (cond
      (empty? args) opts
      (= "--config" (first args))
      (recur (assoc opts :config-path (second args)) (drop 2 args))
      :else (recur opts (rest args)))))

(def ^:private mutating-commands
  #{"select" "unselect" "region" "clear"})

(defn mutating?
  "True when running `cmd` changes filters that should be persisted."
  [cmd]
  (contains? mutating-commands cmd))

(defn dispatch
  "Run `cmd` against `session`. Returns the updated session,
   or ::exit when the user asked to quit."
  [session cmd args]
  (case cmd
    ("exit" "quit") ::exit
    "help"     (cmd/handle-help session)
    "status"   (cmd/handle-status session)
    "select"   (cmd/handle-select session args)
    "unselect" (cmd/handle-unselect session args)
    "region"   (cmd/handle-region session args)
    "search"   (cmd/handle-search session args)
    "info"     (cmd/handle-info session args)
    "download" (cmd/handle-download session args)
    "extract"  (cmd/handle-extract session args)
    "fix"      (cmd/handle-fix session args)
    "license"  (cmd/handle-license-all session args)
    "sync"     (cmd/handle-sync session)
    "refresh"  (cmd/handle-refresh session args)
    "clear"    (cmd/handle-clear session)
    (do (c/say (c/color :red "unknown command:") cmd
                 (c/color :dim "(try 'help')"))
        session)))

(defn- read-and-dispatch [session config-path]
  (print (sess/prompt-str session)) (flush)
  (let [line (read-line)]
    (cond
      (nil? line)                          ::exit
      (str/blank? line)                    session
      :else
      (let [[cmd & args] (str/split (str/trim line) #"\s+")
            result (dispatch session cmd args)]
        (when (and (not= ::exit result) (mutating? cmd))
          (cfg/save-session config-path
                            (:selected-types result)
                            (:selected-regions result)))
        result))))

(defn run
  "Run the interactive REPL using the config at `config-path`."
  [config-path]
  (let [config (cfg/load-config config-path)]
    (println)
    (c/say (c/color :bold "zeus") "— NoPayStation Interactive Browser")
    (c/say (c/color :dim "type 'help' for commands, 'exit' to quit"))
    (println)
    (loop [session (sess/new-session config)]
      (let [next (try (read-and-dispatch session config-path)
                      (catch Exception e
                        (c/say (c/color :red "error:") (.getMessage e))
                        session))]
        (cond
          (= ::exit next) (c/say (c/color :dim "👋 goodbye"))
          :else (recur next))))))

(defn -main
  "Entry point."
  [& argv]
  (let [{:keys [config-path]} (parse-args argv)]
    (run config-path)))
