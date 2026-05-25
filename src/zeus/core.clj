(ns zeus.core
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [zeus.commands :as cmd]
            [zeus.config :as cfg]
            [zeus.session :as sess]
            [zeus.view :as view]))

(def cli-options
  [["-c" "--config PATH" "Path to YAML config file"
    :default "config.yaml"]])

(defn parse-args
  "Parse top-level CLI args. Recognizes --config <path>."
  [argv]
  (let [{:keys [options]} (cli/parse-opts argv cli-options)]
    {:config-path (:config options)}))

(def ^:private mutating-commands
  #{"select" "unselect" "region" "clear"})

(defn mutating?
  "True when running `cmd` changes filters that should be persisted."
  [cmd]
  (contains? mutating-commands cmd))

(def exit-commands #{"exit" "quit"})

(def handlers
  "Map of command name to a (fn [session args]) handler."
  {"help"     (fn [s _] (cmd/handle-help s))
   "status"   (fn [s _] (cmd/handle-status s))
   "select"   cmd/handle-select
   "unselect" cmd/handle-unselect
   "region"   cmd/handle-region
   "search"   cmd/handle-search
   "info"     cmd/handle-info
   "download" cmd/handle-download
   "extract"  cmd/handle-extract
   "fix"      cmd/handle-fix
   "license"  cmd/handle-license-all
   "sync"     (fn [s _] (cmd/handle-sync s))
   "refresh"  cmd/handle-refresh
   "clear"    (fn [s _] (cmd/handle-clear s))})

(defn dispatch
  "Run `cmd` against `session`. Returns ::exit for exit commands,
   or {:session ..., :events [...]} from the matched handler. Binds
   cmd/*emit!* to view/render! so handlers can stream live events."
  [session cmd args]
  (cond
    (exit-commands cmd) ::exit
    :else
    (binding [cmd/*emit!* view/render!]
      (if-let [handler (handlers cmd)]
        (handler session args)
        {:session session :events [[:unknown-command cmd]]}))))

(defn- read-and-dispatch [session config-path]
  (print (sess/prompt-str session)) (flush)
  (let [line (read-line)]
    (cond
      (nil? line)       ::exit
      (str/blank? line) session
      :else
      (let [[cmd & args] (str/split (str/trim line) #"\s+")
            outcome (dispatch session cmd args)]
        (if (= ::exit outcome)
          ::exit
          (let [{:keys [session events]} outcome]
            (view/render-all! events)
            (when (mutating? cmd)
              (cfg/save-session config-path
                                (:selected-types session)
                                (:selected-regions session)))
            session))))))

(defn run
  "Run the interactive REPL using the config at `config-path`."
  [config-path]
  (let [config (cfg/load-config config-path)]
    (view/render! [:banner])
    (loop [session (sess/new-session config)]
      (let [next (try (read-and-dispatch session config-path)
                      (catch Exception e
                        (view/render! [:repl-error (.getMessage e)])
                        session))]
        (cond
          (= ::exit next) (view/render! [:goodbye])
          :else (recur next))))))

(defn -main
  "Entry point."
  [& argv]
  (let [{:keys [config-path]} (parse-args argv)]
    (run config-path)))
