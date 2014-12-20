(ns com.manigfeald.sofp.signals
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]))

(defrecord SignalHandler [pagerank]
  component/Lifecycle
  (start [this]
    (log/info "start signal handler")
    this)
  (stop [this]
    this)
  clojure.lang.IFn
  (invoke [_ signal-name handler]
    (sun.misc.Signal/handle
     (sun.misc.Signal. signal-name)
     (reify
       sun.misc.SignalHandler
       (handle [_ _]
         (handler))))))
