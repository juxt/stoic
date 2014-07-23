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

(defn read-config [file-path]
  "Reads the edn based config from the specified file"
  (log/info "Reading config from file: " file-path)
  (let [config (edn/read-string (slurp file-path))]
    (log/debug "Config: " config)
    config))

(defn- config-file-change? [config-path f]
  "Filter out all file system events that do not match the stoic config file modification or creation"
  (and (= config-path (.getAbsolutePath (:file f))) (not= (:action f) :delete)))

(defn reload-config! [config-atom config-path watch-fn-atom file-events]
  "If the stoic config file has changed, reload the config and call the optional watch function"
  (when (not-empty (filter (partial config-file-change? config-path) file-events))
    (log/info "Reloading config...")
    (reset! config-atom (read-config config-path))
    (when @watch-fn-atom
      (@watch-fn-atom))))

(defrecord FileConfigSupplier []
  stoic.protocols.config-supplier/ConfigSupplier
  component/Lifecycle

  (start [this]
    (let [config-path (*read-config-path*)
          config-dir (.getParentFile (io/as-file config-path))]
      (try
        (let [
               config-atom (atom (read-config config-path))
               watch-fn-atom (atom nil)
               config-watcher (watch-dir (partial reload-config! config-atom config-path watch-fn-atom) config-dir)]
          (assoc this :config config-atom :config-watcher config-watcher :watch-fn watch-fn-atom))
        (catch AccessDeniedException e
          (do
            (log/fatal "Unable to assign watcher to directory " (.getAbsolutePath config-dir) " check permissions")
            (throw e))))))

  (stop [this]
    (when-let [watcher (:config-watcher this)]
      (close-watcher watcher))
    this)

  (fetch [this component]
    (@(:config this) component))

  (watch! [{:keys [watch-fn]} k watcher-function]
    (reset! watch-fn watcher-function)))

(defn config-supplier []
  (FileConfigSupplier.))