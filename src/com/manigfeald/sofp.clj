(ns com.manigfeald.sofp
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.manigfeald.sofp.protocols :as p]
            [com.manigfeald.sofp.mpd :as mpd]
            [com.manigfeald.sofp.executor :as executor]
            [com.manigfeald.sofp.graph :as graph]
            [com.manigfeald.sofp.history :as history]
            [com.manigfeald.sofp.pagerank :as pr]
            [com.manigfeald.sofp.signals :as signals]
            [com.manigfeald.sofp.watcher :as watcher]
            [com.stuartsierra.component :as component]
            [com.manigfeald.sofp.repl :as repl]
            ;; [com.manigfeald.sofp.idlewatch :as iw]
            )
  (:gen-class))

(defn song-finder-loop [mpd storage history run?]
  (fn []
    (try
      (let [recent-recommends (atom ())]
        (loop []
          (log/trace "top of song-finder-loop")
          (let [playlist (mpd/playlist mpd)]
            (when (> 6 (count playlist))
              (log/trace "playlist low")
              (loop [i 20]
                (when (pos? i)
                  (let [playlist (mpd/playlist mpd)
                        last-song (or (last playlist) @(:last-played history))]
                    (log/trace "last song in playlist" last-song)
                    (if-let [song (graph/weighted-walk storage last-song)]
                      (do
                        (log/trace "weighted walk" last-song "->" song)
                        (if-not (or (some #{song} (map :song @(:history history)))
                                    (some #{song} @recent-recommends))
                          (do
                            (swap! recent-recommends conj song)
                            (swap! recent-recommends (partial (comp doall take) 10))
                            (mpd/queue-song mpd song)
                            (recur (dec i)))
                          (recur 0)))
                      (recur 0))))))
            (log/trace "song-finder-loop idle")
            (mpd/idle-for-playlist-change mpd)
            (recur))))
      (catch Exception e
        (log/error e "whoops")
        (throw e)))))

(defrecord NextSongFinder [mpd storage history exec]
  component/Lifecycle
  (start [this]
    (log/info "starting next song finder")
    (let [run? (atom true)]
      (assoc this
        :run? run?
        :worker (exec (song-finder-loop mpd storage history run?)))))
  (stop [this]
    (reset! (:run? this) false)
    (deref (:worker this))
    this)
  p/AWait
  (-wait [this]
    (deref (:worker this))
    this))

(def sys nil)

(defn run-system [sys success failure wait]
  (try
    (let [s (component/start sys)]
      (log/info "started")
      (alter-var-root #'sys (constantly s))
      (try
        (component/stop (component/update-system s wait p/-wait))
        success
        (catch Exception e
          (log/error e "whoops")
          (component/update-system s (keys s) (fn [obj]
                                                (try
                                                  (component/stop obj)
                                                  (catch Exception e
                                                    (log/error e "whoops")
                                                    obj))))
          failure)))
    (catch Exception e
      (log/error e "whoops")
      failure)))

(defn -main [& args]
  (let [f (io/file (System/getenv "HOME") ".sofp.db")
        mpd-host (System/getenv "MPD_HOST")
        mpd-port (Long/parseLong (System/getenv "MPD_PORT"))]
    (when (.contains mpd-host "@")
      (throw (IllegalArgumentException. "sofp doesn't support passwords (yet)")))
    (-> (component/system-map
         ;; :idlewatch (iw/map->IdleWatcher {})
         :pagerank (pr/map->PageRanker {})
         :exec (executor/map->Exec {})
         :history (history/map->MPDHistory {})
         :storage (graph/map->DerbyDatabase {})
         ;; TODO: do something with this
         :mpd (mpd/map->MPDThing {:server mpd-host
                                  :port mpd-port})
         :song-watcher (watcher/map->SongHistoryWatcher {})
         :next-song-finder (map->NextSongFinder {})
         :signals (signals/map->SignalHandler {})
         :repl (repl/map->Repl {})
         :graph (graph/map->Graph {})
         :database-connection (graph/map->DatabaseConnection {:file f}))
        (component/system-using
         {:storage {:con :database-connection :g :graph}
          :mpd []
          :exec []
          :graph {:con :database-connection}
          :pagerank {:db :storage :sig :signals}
          :song-watcher [:mpd :storage :history :exec]
          :next-song-finder [:mpd :storage :history :exec]
          :signals []
          :history [:mpd :exec]
          :repl [:signals :exec]
          ;; :idlewatch {:mpd :mpd
          ;;             :con :database-connection
          ;;             :exec :exec}
          })
        (run-system 0 1 [:exec])
        (System/exit))))
