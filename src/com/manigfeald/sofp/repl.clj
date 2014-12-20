(ns com.manigfeald.sofp.repl
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [clojure.tools.nrepl.server :refer [start-server stop-server]]))

(defrecord Repl [signals exec]
  component/Lifecycle
  (start [this]
    (log/trace "starting repl")
    (let [run? (atom true)
          queue (java.util.concurrent.LinkedTransferQueue.)]
      (signals "TRAP" (fn [] (future (.offer queue :token))))
      (assoc this
        :run? run?
        :worker (exec (fn []
                        (try
                          (while @run?
                            (.take queue)
                            (log/info "starting nrepl on 7888")
                            (let [s (start-server
                                     :host "127.0.0.1"
                                     :port 7888)]
                              (.take queue)
                              (log/info "stopping nrepl on 7888")
                              (stop-server s)))
                          (catch Exception e
                            (log/error e "whoops")
                            (throw e))))))))
  (stop [this]
    (reset! (:run? this) false)
    (future-cancel (:worker this))
    (deref (:worker this))
    this))
