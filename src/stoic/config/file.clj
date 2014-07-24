(ns stoic.config.file
  "Loads stoic config from a file containing edn.The file is watched for changes and config is reloaded when they occur."
  (:import (java.nio.file AccessDeniedException))
  (:require [stoic.protocols.config-supplier]
            [clojure.tools.logging :as log]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [environ.core :as environ]
            [com.stuartsierra.component :as component]
            [juxt.dirwatch :refer (watch-dir close-watcher)]))

(defn read-config-path []
  (let [path (environ/env :am-config-path)]
    (when (string/blank? path)
      (throw (IllegalArgumentException.
               "Please set AM_CONFIG_PATH environment variable to the absolute path of your application config file")))
    path))

(def ^:dynamic *read-config-path* read-config-path)

(defn enabled? []
  (try
    (*read-config-path*)
    true
    (catch IllegalArgumentException e false)))

(defn- deep-merge [& maps]
  (if (every? map? maps)
    (apply merge-with deep-merge maps)
    (last maps)))

(defn read-config [config-files]
  "Reads the edn based config from the specified file"
  (log/info "Reading config from files: " (map #(.getAbsolutePath %) config-files))
  (let [config (apply deep-merge (map #(edn/read-string (slurp %)) config-files))]
    (log/debug "Config: " config)
    config))

(defn- config-file-change? [config-files f]
  "Filter out all file system events that do not match the stoic config file modification or creation"
  (and (contains? (set config-files) (:file f)) (not= (:action f) :delete)))

(defn reload-config! [config-atom config-files watch-fn-atom file-events]
  "If the stoic config file has changed, reload the config and call the optional watch function"
  (when (not-empty (filter (partial config-file-change? config-files) file-events))
    (log/info "Reloading config...")
    (reset! config-atom (read-config config-files))
    (when @watch-fn-atom
      (@watch-fn-atom))))

(defn- config-files [config-paths system-name]
  (for [path config-paths
        :let [f (io/file path)]]
    (if (.isDirectory f)
      (let [system-settings (io/file f (str (name system-name) ".edn"))]
        (when (.exists system-settings) system-settings))
      f)))

(defrecord FileConfigSupplier [system-name]
  stoic.protocols.config-supplier/ConfigSupplier
  component/Lifecycle

  (start [this]
    (let [config-paths (string/split (*read-config-path*) #":")
          config-files (config-files config-paths system-name)
          config-dirs  (set (map #(.getParentFile %) config-files))]
      (println "Config files" config-files)
      (println "Config dirs" config-dirs)
      (try
        (let [config-atom (atom (read-config config-files))
              watch-fn-atom (atom nil)
              config-watcher (apply watch-dir (partial reload-config! config-atom config-files watch-fn-atom) config-dirs)]
          (assoc this :config config-atom :config-watcher config-watcher :watch-fn watch-fn-atom))
        (catch AccessDeniedException e
          (do
            (log/fatal "Unable to assign watcher to directories " (map #(.getAbsolutePath %) config-dirs) " check permissions")
            (throw e))))))

  (stop [this]
    (when-let [watcher (:config-watcher this)]
      (close-watcher watcher))
    this)

  (fetch [this component]
    (@(:config this) component))

  (watch! [{:keys [watch-fn]} k watcher-function]
    (reset! watch-fn watcher-function)))

(defn config-supplier [system-name]
  (FileConfigSupplier. system-name))
