(ns stoic.components.settings
  "A generic component that exists purely to wrap settings."
  (:require [com.stuartsierra.component :as component]))

(defrecord SettingsComponent [settings])
