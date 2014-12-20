(ns com.manigfeald.sofp.watcher
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [com.manigfeald.sofp.protocols :as p]
            [com.manigfeald.sofp.graph :as graph]
            [com.manigfeald.sofp.history :as history]))

(defn song-watching-loop [mpd storage history run?]
  (fn []
    (try
      (while @run?
        (let [prev-song (history/playing-song history)]
          (log/trace "prev-song" prev-song)
          (loop []
            (apply p/-wait [history])
            (let [now-song (history/playing-song history)]
              (if (= now-song prev-song)
                (recur)
                (graph/increment-edge storage prev-song now-song))))))
      (catch Exception e
        (log/error e "whoops")
        (throw e)))))

(defrecord SongHistoryWatcher [mpd storage history exec]
  component/Lifecycle
  (start [this]
    (log/info "starting song history watcher")
    (let [run? (atom true)
          last-played (atom nil)]
      (assoc this
        :run? run?
        :worker (exec (song-watching-loop mpd storage history run?)))))
  (stop [this]
    (reset! (:run? this) false)
    (deref (:worker this))
    this)
  p/AWait
  (-wait [this]
    (deref (:worker this))
    this))
