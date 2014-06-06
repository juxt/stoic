(ns stoic.config.exhibitor
  (:require [curator.framework :refer [exponential-retry]]
            [stoic.config.env :refer :all])
  (:import [org.apache.curator.ensemble.exhibitor
            Exhibitors
            ExhibitorEnsembleProvider
            Exhibitors$BackupConnectionStringProvider
            DefaultExhibitorRestClient]
           [org.apache.curator.retry
            RetryNTimes]
           [org.apache.curator.retry ExponentialBackoffRetry]
           [org.apache.curator.framework CuratorFramework CuratorFrameworkFactory]
           [org.apache.curator.framework.imps CuratorFrameworkState]
           [java.util.concurrent TimeUnit]))

(defn- ensemble-provider []
  (let [[host port] (clojure.string/split (exhibitor-host) #":")]
    (ExhibitorEnsembleProvider.
     (Exhibitors. [host] (Integer/parseInt port)
                  (proxy [Exhibitors$BackupConnectionStringProvider] []
                    (getBackupConnectionString []
                      (throw (Exception. "No backup is possible.")))))
     (DefaultExhibitorRestClient.)
     "/exhibitor/v1/cluster/list"
     5000 ;; Poll every
     (RetryNTimes. 10 1000))))

(defn exhibitor-framework []
  (-> (doto (CuratorFrameworkFactory/builder)
        (.ensembleProvider (ensemble-provider))
        (.retryPolicy (exponential-retry 1000 10))
        (.connectionTimeoutMs 500)
        (.sessionTimeoutMs (* 40 1000)))
      (.build)))
