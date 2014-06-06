(ns stoic.config.zk-test
  (:require [stoic.config.zk :refer :all]
            [stoic.config.data :refer :all]
            [clojure.test :refer :all]
            [stoic.protocols.config-supplier :as cs]
            [com.stuartsierra.component :as component]))

(deftest can-write-and-read-from-zookeeper
  (let [expected {:a :b}
        zk (component/start (zk-config-supplier))]
    (add-to-zk (connect) (path-for :default :foo) expected)
    (is (= {:a :b} (cs/fetch zk :foo)))))
