(ns stoic.bootstrap
  "Bootstrap a component system with components and settings."
  (:require [com.stuartsierra.component :as component]
            [stoic.components.foo]
            [stoic.protocols.config-supplier :as cs]
            [stoic.config.zk]
            [stoic.config.curator]
            [clojure.tools.logging :as log]))

(defn- choose-supplier []
  (stoic.config.curator/config-supplier))

(def ^:dynamic *choose-supplier* choose-supplier)

(defn- inject-components
  "Inject components associating in the respective settings as an atom.
   Returns a new SystemMap."
  [component-settings system]
  (apply component/system-map
         (reduce into []
                 (for [[k c] system]
                   [k (assoc c :settings (get component-settings k))]))))

(defn- fetch-settings
  "Fetch settings from the config supplier and wrap in atoms."
  [config-supplier system]
  (into {} (for [[k] system]
             [k (atom (cs/fetch config-supplier k))])))

(defn- bounce-component! [config-supplier k c settings-atom]
  (let [settings (cs/fetch config-supplier k)]
    (when (not= @settings-atom settings)
      (component/stop c)
      (reset! settings-atom settings)
      (component/start c))))

(defn- bounce-components-if-config-changes!
  "Add watchers to config to bounce relevant component if config changes."
  [config-supplier components component-settings]
  (doseq [[k c] components
          :let [settings-atom (get component-settings k)]]
    (cs/watch! config-supplier k
               (partial bounce-component! config-supplier k c settings-atom))))

(defn bootstrap
  "Inject system with settings fetched from a config-supplier.
   Components will be bounced when their respective settings change.
   Returns a SystemMap with Stoic config attached."
  ([system]
     (bootstrap (*choose-supplier*) system))
  ([config-supplier system]
     (let [config-supplier-component (component/start config-supplier)
           component-settings (fetch-settings config-supplier-component system)
           system (inject-components component-settings system)]
       (bounce-components-if-config-changes!
        config-supplier-component system component-settings)
       (assoc system :stoic-config config-supplier-component))))

(defn start-safely
  "Will start a system.
   If an error occurs in any of the components when the system starts,
   the error will be caught and an attempted system shutdown performed."
  [system]
  (try
    (component/start system)
    (catch Throwable t
      (try
        (log/error t "Could not start up system, attempting to shutdown")
        (println "Error occuring starting system, check logs.")
        (component/stop system)
        (catch Throwable t
          (log/error t "Could not shutdown system")
          system)))))
