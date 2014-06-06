(ns stoic.protocols.config-supplier)

(defprotocol ConfigSupplier
  (fetch [this k])

  (watch! [this k watcher-fn]))
