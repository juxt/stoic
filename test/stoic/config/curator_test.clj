(ns stoic.config.curator-test
  (:require [stoic.config.curator :refer :all]
            [stoic.config.env :refer :all]
            [stoic.config.data :refer :all]
            [clojure.test :refer :all]
            [stoic.protocols.config-supplier :as cs]
            [com.stuartsierra.component :as component]))

(deftest can-write-and-read-from-zookeeper
  (let [expected {:a :b}
        zk (component/start (config-supplier :foo))]
    (add-to-zk (connect) (path-for (zk-root) :foo) expected)
    (is (= expected (cs/fetch zk :foo)))))

#_(deftest deep-merges-multiple-paths-in-config
  (let [config (:config (component/start (->FileConfigSupplier :test-application)))]
    (is (= {:ptth-tik {:abc :def}
            :http-kit {:port 8080 :threads 16}
            :test-application {:pqr :stu}}
           @config))))
