(ns stoic.components.foo
  (:require [com.stuartsierra.component :as component]))

(defrecord Foo [settings]
  component/Lifecycle

  (start [component]
    (assoc component :started? true))

  (stop [component]
    (assoc component :stop? true)))
