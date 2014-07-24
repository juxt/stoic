(ns stoic.bootstrap-test
  (:use [clojure.core.async :only [chan timeout >!! <!! buffer alts!!]])
  (:require [stoic.config.curator :as stoic-zk]
            [stoic.config.env :refer [zk-root]]
            [clojure.test :refer :all]
            [stoic.protocols.config-supplier :as cs]
            [com.stuartsierra.component :as component]
            [stoic.bootstrap :as b]
            [stoic.config.data :refer :all]))

(defrecord TestAsyncComponent [starts stops]
  component/Lifecycle

  (start [{:keys [settings]}]
    (>!! starts (:a @settings)))

  (stop [{:keys [settings]}]
    (>!! stops (:a @settings))))

(defmacro harness [& body]
  `(let [~'client (stoic-zk/connect)]
     (try
       ~@body
       (finally
         (stoic-zk/close ~'client)))))

(deftest can-bounce-component-on-config-change
  (harness
   (stoic-zk/add-to-zk client (path-for (zk-root) :test) {:a :initial-value})

   (let [starts (chan (buffer 1))
         stops (chan (buffer 1))]
     (component/start
      (b/bootstrap
       (component/system-map :test (->TestAsyncComponent starts stops))))

     (is (= :initial-value (first (alts!! [(timeout 2000) starts]))))

     (stoic-zk/add-to-zk client (path-for (zk-root) :test) {:a :b})

     (is (= :initial-value (first (alts!! [(timeout 2000) stops]))))
     (is (= :b (first (alts!! [(timeout 2000) starts]))))

     (stoic-zk/add-to-zk client (path-for (zk-root) :test) {:a :c})

     (is (= :b (first (alts!! [(timeout 2000) stops]))))
     (is (= :c (first (alts!! [(timeout 2000) starts])))))))

(deftest can-not-bounce-component-if-doesnt-change
  (harness
   (stoic-zk/add-to-zk client (path-for (zk-root) :test) {:a :initial-value})

   (let [starts (chan (buffer 1))
         stops (chan (buffer 1))]
     (component/start
      (b/bootstrap
       (component/system-map :test (->TestAsyncComponent starts stops))))

     (is (= :initial-value (first (alts!! [(timeout 2000) starts]))))

     (stoic-zk/add-to-zk client (path-for (zk-root) :test) {:a :initial-value})

     (let [t-c (timeout 2000)
           [v c] (alts!! [t-c stops])]
       (is (= t-c c))))))

(defrecord TestComponent [s]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]))

(defrecord TestDependentComponent [s funk]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]))

(deftest component-with-dependencies-get-injected
  (harness
   (stoic-zk/add-to-zk client (path-for (zk-root) :test1) {:a :test-1-value})
   (stoic-zk/add-to-zk client (path-for (zk-root) :test2) {:a :test-2-value})

   (let [system (component/start
                 (b/bootstrap
                  (component/system-map :test1 (map->TestComponent {:s "sad"})
                                        :test2 (component/using
                                                (map->TestDependentComponent {:s "fo"})
                                                [:test1]))))]

     (is (= :test-1-value (-> system :test2 :test1 :settings deref :a))))))

(defrecord TestBarfingComponent []
  component/Lifecycle
  (start [this]
    (throw (IllegalArgumentException. "Component blew up")))
  (stop [this]))

(deftest components-are-shutdown-safely-if-component-fails-during-startup
  (harness
   (stoic-zk/add-to-zk client (path-for (zk-root) :test1) {:a :initial-value})
   (let [starts (chan (buffer 1))
         stops (chan (buffer 1))
         system
         (b/start-safely
          (b/bootstrap
           (component/system-map :test1 (->TestAsyncComponent starts stops)
                                 :test2 (component/using
                                         (map->TestBarfingComponent {})
                                         [:test1]))))]
     (is (= :initial-value (first (alts!! [(timeout 2000) starts]))))
     (is (= :initial-value (first (alts!! [(timeout 2000) stops])))))))

(deftest system-with-a-name-has-application-settings-merged
  (harness
   (stoic-zk/add-to-zk client (path-for (zk-root) :test1) {:a :test-1-value})
   (stoic-zk/add-to-zk client (path-for (zk-root) :test-application) {:y :z})

   (testing "Application settings are merged with component settings"
     (let [system (component/start
                   (b/bootstrap
                    (component/system-map :name :test-application
                                          :test1 (map->TestComponent {}))))]

       (is (= :test-application (-> system :name)))
       (is (= {:a :test-1-value
               :test-application {:y :z}} (-> system :test1 :settings deref)))))))
