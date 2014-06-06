(ns stoic.config.env
  (:require [environ.core :as environ]))

(defn exhibitor-host []
  (not-empty (environ/env :am-exhibitor-hosts)))

(defn zk-ips
  "Zookeeper IPs."
  []
  (or (not-empty (environ/env :am-zk-hosts)) "localhost:2181"))

(defn zk-root
  "Zookeeper Root."
  [] (keyword (or (not-empty (environ/env :am-zk-root)) :default)))
