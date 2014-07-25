(ns stoic.config.file-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [stoic.config.data :refer :all]
            [stoic.config.file :refer :all]))

(deftest test-read-file []
  (let [am-config-path "./test-resources/config/test.edn"]
    (is (not= (read-config [(io/file am-config-path)]) nil))))

(deftest test-is-not-enabled []
  (is (= (enabled?) false)))

(deftest test-is-enabled []
  (let [am-config-path "./test-resources/config/test.edn"]
    (binding [*read-config-path* (constantly am-config-path)]
      (is (enabled?)))))

(deftest test-read-config []
  (let [am-config-path "./test-resources/config/test.edn"]
    (binding [*read-config-path* (constantly am-config-path)]
      (let [config (:config (component/start (->FileConfigSupplier :foo)))]
        (is (= {:port 8080 :threads 4} (:http-kit @config)))))))

(deftest discovers-file-called-system-name-when-supplied-with-directory
  (let [am-config-path "./test-resources/config"]
    (binding [*read-config-path* (constantly am-config-path)]
      (let [config (:config (component/start (->FileConfigSupplier :test-application)))]
        (is (= {:http-kit {:threads 16}
                :test-application {:pqr :stu}}
               @config))))))

(deftest deep-merges-multiple-files-in-config
  (let [am-config-path "./test-resources/config/test.edn:./test-resources/config"]
    (binding [*read-config-path* (constantly am-config-path)]
      (let [config (:config (component/start (->FileConfigSupplier :test-application)))]
        (is (= {:ptth-tik {:abc :def}
                :http-kit {:port 8080 :threads 16}
                :test-application {:pqr :stu}}
               @config))))))
