(ns stoic.config.curator
  (:require [curator.framework :refer (curator-framework)]
            [curator.discovery :refer (service-discovery service-instance service-provider instance instances services note-error)]
            [stoic.protocols.config-supplier]
            [stoic.config.data :refer :all]
            [stoic.config.env :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [stoic.config.exhibitor :refer :all])
  (:import [org.apache.curator.framework.api
            CuratorWatcher CuratorEventType]))

(defn connect
  ([] (connect (or (and (exhibitor-host) (exhibitor-framework))
                   (curator-framework (zk-ips)))))
  ([client]
     (.start client)
     client))

(defn close [client]
  (.close client))

(defn add-to-zk [client path m]
  (when-not (.. client checkExists (forPath path))
    (.. client create (forPath path nil)))
  (.. client setData (forPath path (serialize-form m))))

(defn read-from-zk [client path]
  (deserialize-form (.. client getData (forPath path))))

(defn- watch-path [client path watcher]
  (.. client checkExists watched (usingWatcher watcher)
      (forPath path)))

(defrecord CuratorConfigSupplier [root system-name]
  stoic.protocols.config-supplier/ConfigSupplier
  component/Lifecycle

  (start [{:keys [client] :as this}]
    (if client this (assoc this :client (connect))))

  (stop [{:keys [client] :as this}]
    (when client
      (log/info "Disconnecting from ZK")
      (close client))
    this)

  (fetch [{:keys [client]} k]
    (let [path (path-for root k)]
      (when-not (.. client checkExists (forPath path))
        (.. client create (forPath path nil)))
      (read-from-zk client path)))

  (watch! [{:keys [client]} k watcher-fn]
    (let [path (path-for root k)]
      (watch-path client path
                  (reify CuratorWatcher
                    (process [this event]
                      (when (= :NodeDataChanged (keyword (.. event getType name)))
                        (log/info "Data changed, firing watcher" event)
                        (watcher-fn)
                        (watch-path client path this))))))))

(defn config-supplier [system-name]
  (CuratorConfigSupplier. (zk-root) system-name))
