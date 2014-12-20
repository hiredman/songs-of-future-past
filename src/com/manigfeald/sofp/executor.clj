(ns com.manigfeald.sofp.executor
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [com.manigfeald.sofp.protocols :as p])
  (:import (clojure.lang IFn)
           (java.util.concurrent Executors
                                 ExecutorService)))

(defrecord Exec []
  component/Lifecycle
  (start [this]
    (log/info "start exec")
    (assoc this
      :e (Executors/newCachedThreadPool)
      :barrier (promise)
      :tasks (atom [])))
  (stop [this]
    (doseq [task @(:tasks this)]
      (future-cancel task))
    this)
  p/AWait
  (-wait [this]
    @(:barrier this))
  IFn
  (invoke [this fun]
    (let [f (.submit
             (:e this)
             (reify
               Callable
               (call [_]
                 (try
                   (fun)
                   (catch Exception e
                     (log/error e "whoops")
                     (throw e))
                   (finally
                     (deliver (:barrier this) nil))))))]
      (swap! (:tasks this) conj f)
      f)))
