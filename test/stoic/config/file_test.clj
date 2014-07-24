(ns stoic.config.file-test
  (:require [stoic.config.file :refer :all]
            [stoic.config.data :refer :all]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]))

(deftest test-read-file []
  (let [am-config-path "./test-resources/config/test.edn"]
    (is (not= (read-config am-config-path) nil))))

(deftest test-is-not-enabled []
  (is (= (enabled?) false)))

(deftest test-is-enabled []
  (let [am-config-path "./test-resources/config/test.edn"]
    (binding [*read-config-path* (constantly am-config-path)]
      (is (enabled?)))))

(deftest test-read-config []
  (let [am-config-path "./test-resources/config/test.edn"]
    (binding [*read-config-path* (constantly am-config-path)]
      (let [config (:config (component/start (->FileConfigSupplier)))]
        (is (= {:port 8080 :threads 4} (:http-kit @config)))))))
