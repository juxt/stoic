(ns stoic.config.zk
  "Namespace to faciliate Stoic interaction with Zookeeper."
  (:require [zookeeper :as zk]
            [zookeeper.data :as zk-data]
            [stoic.protocols.config-supplier]
            [stoic.config.data :refer :all]
            [stoic.config.env :refer :all]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]))

(defn connect []
  (log/info "Connecting to ZK")
  (zk/connect (zk-ips)
              :timeout-msec 10000))

(defn close [client]
  (zk/close client))

(defn add-to-zk [client path m]
  (when-not (zk/exists client path)
    (zk/create-all client path :persistent? true))
  (let [v (:version (zk/exists client path))]
    (zk/set-data client path (serialize-form m) v)))

(defn read-from-zk [client path]
  (deserialize-form (:data (zk/data client path))))

(defn profiles [client]
  (map keyword (zk/children client "/stoic")))

(defn components [client profile]
  (map keyword (zk/children client (format "/stoic/%s/components" profile))))

(defrecord ZkConfigSupplier [root]
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
      (when-not (zk/exists client path)
        (zk/create-all client path :persistent? true))
      (read-from-zk client path)))

  (watch! [{:keys [client]} k watcher-fn]
    (let [path (path-for root k)]
      (zk/exists client path :watcher
                 (fn the-watcher [event]
                   (when (= :NodeDataChanged (:event-type event))
                     (log/info "Data changed, firing watcher" event)
                     (watcher-fn)
                     (zk/exists client path :watcher the-watcher)))))))

(defn zk-config-supplier []
  (ZkConfigSupplier. (zk-root)))
