(ns com.manigfeald.sofp.history
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [com.manigfeald.sofp.protocols :as p]
            [com.manigfeald.sofp.mpd :as mpd])
  (:import (clojure.lang PersistentQueue)
           (java.util.concurrent Semaphore)))

(defn trim [q n]
  (doall (take n q)))

(defmacro either [a b]
  `(let [p# (promise)
         a# (future (deliver p# (try
                                  [~a]
                                  (catch Throwable t#
                                    [nil t#]))))
         b# (future (deliver p# (try
                                  [~b]
                                  (catch Throwable t#
                                    [nil t#]))))
         [result# error#] @p#]
     (future-cancel a#)
     (future-cancel b#)
     (if error#
       (throw error#)
       result#)))

;; TODO: clear last played if the player hasn't been playing for 30 minutes
(defn playing-song [history]
  (if-let [song @(:last-played history)]
    song
    (do
      (p/-wait history)
      (recur history))))

(defn playing-song* [history]
  (when-let [h (->> (take-while #(= :playing (:status %)) @history)
                    (partition-by :song )
                    (first)
                    (seq))]
    (let [{t-s1 :t-s
           status1 :status
           song1 :song} (first h)
           {t-s2 :t-s
            status2 :status
            song2 :song} (last h)]
      (when (> (- t-s1 t-s2) (* 1000 60))
        (:song (first h))))))

(defrecord MPDHistory [mpd exec]
  component/Lifecycle
  (start [this]
    (log/info "start mpd history")
    (let [history (atom ())
          run? (atom true)
          last-played (atom nil)
          waiter (doto (Semaphore. 100 true)
                   (.drainPermits))
          worker (exec
                  (fn []
                    (try
                      (while @run?
                        (let [t-s (System/currentTimeMillis)
                              status (mpd/player-state mpd)
                              song (mpd/current-song mpd)
                              t-e (System/currentTimeMillis)
                              item {:t-s t-s
                                    :t-e t-e
                                    :status status
                                    :song song}]
                          (when (or (not= status (:status (first @history)))
                                    (not= song (:song (first @history))))
                            (log/trace item))
                          (swap! history conj item))
                        (swap! history trim 10000)
                        (when-let [song (playing-song* history)]
                          (when (not= song @last-played)
                            (reset! last-played song)))
                        ;; 1 minute sampling
                        ;; maybe change to idling for mpd events
                        ;; between samples
                        ;; need to update playing-song*
                        (either (Thread/sleep (* 60 1000))
                                (mpd/idle-for-playlist-change mpd))
                        ;; (Thread/sleep (* 60 1000))
                        (.release waiter 100)
                        (.acquire waiter 100))
                      (catch Throwable t
                        (log/error t "whoops")
                        (throw t)))))]
      (assoc this
        :waiter waiter
        :last-played last-played
        :run? run?
        :history history
        :worker worker)))
  (stop [this]
    (reset! (:run? this) false)
    @(:worker this)
    this)
  p/AWait
  (-wait [this]
    (try
      (.acquire ^Semaphore (:waiter this))
      (finally
        (.release ^Semaphore (:waiter this))))
    nil))
