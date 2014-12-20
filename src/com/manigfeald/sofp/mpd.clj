(ns com.manigfeald.sofp.mpd
  (:require [clojure.tools.logging :as log]
            [com.manigfeald.sofp.protocols :as p]
            [com.stuartsierra.component :as component])
  (:import (org.bff.javampd MPD
                            MPDCommandExecutor
                            MPDCommand
                            MPD$Builder)
           (org.bff.javampd.objects MPDSong)))

(defrecord MPDThing []
  clojure.lang.IFn
  (invoke [this fun]
    (fun (:exec this)))
  component/Lifecycle
  (start [this]
    (log/info "starting mpd")
    (let [mpd (-> (MPD$Builder.)
                  (.server (:server this))
                  (.port (:port this))
                  (.build))
          exec (doto (MPDCommandExecutor.)
                 (.setMpd mpd))]
      (assoc this
        :last-known-state (atom nil)
        :playlist-idle (atom {:phase 0
                              :watchers {}
                              :in-progress? false})
        :player-idle (atom {:phase 0
                            :watchers {}
                            :in-progress? false})
        :mpd mpd
        :exec exec)))
  (stop [this]
    (.close ^MPD (:mpd this))
    this))

(defn current-song [mpd]
  (let [player (.getPlayer ^MPD (:mpd mpd))
        _ (assert player)]
    (when-let [song (.getCurrentSong player)]
      (.getFile song))))

(defn add-promise-to-phase
  [{:keys [phase watchers in-progress?]} p]
  (let [watch-phase (if in-progress?
                      phase
                      (inc phase))]
    {:phase phase
     :watchers (update-in watchers [watch-phase] conj p)
     :in-progress? in-progress?}))

(defn idle [mpd state-key cmd]
  ;; this complicated thing is an attempt to aggregate idling
  (let [p (promise)]
    (swap! (state-key mpd) add-promise-to-phase p)
    (let [idle @(state-key mpd)]
      (if (compare-and-set! (state-key mpd)
                            (assoc idle
                              :in-progress? false)
                            (merge idle {:in-progress? true
                                         :phase (inc (:phase idle))}))
        (do
          (let [phase (:phase @(state-key mpd))]
            (cmd)
            (swap! (state-key mpd) update-in [:in-progress?]
                   (constantly false))
            (doseq [p (get (:watchers @(state-key mpd)) phase)]
              (deliver p nil))
            (swap! (state-key mpd) update-in [:watchers] dissoc phase)))
        @p)))
  nil)

(defn idle-for-playlist-change [mpd]
  ;; (log/trace "idle-for-playlist-change")
  ;; this complicated thing is an attempt to aggregate playlist idling
  (idle mpd :playlist-idle #(.sendCommand ^MPDCommandExecutor (:exec mpd)
                                          "idle"
                                          ^"[Ljava.lang.String;"
                                          (into-array String ["playlist"])))
  nil)

(defn idle-for-any-change [mpd]
  (log/trace "idle-for-any-change")
  (idle mpd :player-idle
        #(.sendCommand ^MPDCommandExecutor (:exec mpd) "idle"))
  nil)

(defn player-state* [mpd]
  (let [player (.getPlayer ^MPD (:mpd mpd))]
    (assert player)
    (if-let [status (.getStatus player)]
      (keyword (.toLowerCase (.replaceAll (.toString status) "^STATUS_" "")))
      (recur mpd))))

(defn player-state [mpd]
  (trampoline
   (fn f []
     (try
       (let [x (player-state* mpd)]
         (reset! (:last-known-state mpd) x)
         x)
       (catch Exception e
         ;; TODO: exponential backoff
         ;; Yay, client libraries that throw random npes
         (Thread/sleep 500)
         f)))))

(defn queue-song [mpd ^String song]
  (assert mpd)
  (assert song)
  (mpd (fn [exec]
         (let [^MPD mpd (:mpd mpd)
               [^MPDSong song] (.listAllSongs (.getDatabase (.getPlaylist mpd)) song)
               _ (assert song)
               already-queued? (some #{song} (.getSongList (.getPlaylist mpd)))]
           (when-not already-queued?
             (log/info "queue" (.getFile song))
             (.addSong (.getPlaylist mpd) song))))))

(defn playlist [mpd]
  (map #(.getFile ^MPDSong %) (.getSongList (.getPlaylist ^MPD (:mpd mpd)))))
