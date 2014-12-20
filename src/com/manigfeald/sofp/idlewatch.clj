(ns com.manigfeald.sofp.idlewatch
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [com.manigfeald.sofp.mpd :as mpd])
  (:import (java.util BitSet)
           (java.nio ByteBuffer)))

(defn now-as-bytes []
  (let [bb (java.nio.ByteBuffer/allocate (* 13 2))
        c (java.util.Calendar/getInstance)]
    (doseq [i [java.util.Calendar/AM_PM
               java.util.Calendar/DAY_OF_MONTH
               java.util.Calendar/DAY_OF_WEEK
               java.util.Calendar/DAY_OF_WEEK_IN_MONTH
               java.util.Calendar/DAY_OF_YEAR
               java.util.Calendar/HOUR_OF_DAY
               java.util.Calendar/MILLISECOND
               java.util.Calendar/MINUTE
               java.util.Calendar/SECOND
               java.util.Calendar/MONTH
               java.util.Calendar/WEEK_OF_MONTH
               java.util.Calendar/WEEK_OF_YEAR
               java.util.Calendar/YEAR]]
      (.putShort bb (short (.get c i))))
    (.array bb)))

(defn normalize [m]
  (let [t (apply + 0 (vals m))]
    (reduce-kv
     (fn [m k v]
       (assoc m k (double (/ v t))))
     m
     m)))

(defn make-model [db]
  (apply merge-with
         (partial merge-with +)
         (for [[bs state] db
               i (range (.size bs))]
           {[i (.get bs i)] {state 1}})))

(defn make-model2 [db]
  (let [f (frequencies
           (for [[bs state] db
                 i (range (.size bs))]
             [i (.get bs i)]))
        id (into {} (for [[k v] f] [k (Math/log (/ (count db) v))]))
        m (make-model db)
        m (into {}
                (for [[position state-counts] m
                      :let [scale (get id position)
                            scaled-state-counts (into {} (for [[state count] state-counts
                                                               :let [scaled-count (* count scale)]
                                                               :when (pos? scaled-count)]
                                                           [state scaled-count]))]
                      :when (not (empty? scaled-state-counts))]
                  [position scaled-state-counts]))]
    m))

;; (defn exec [f]
;;   (future
;;     (try
;;       (f)
;;       (catch Exception e
;;         (log/error e "whoops")))))

(defrecord IdleWatcher [mpd con exec]
  component/Lifecycle
  (start [this]
    (log/info "start idle watcher")
    (when (:first-time? con)
      (jdbc/db-do-commands
       (:con con)
       (jdbc/create-table-ddl
        :idle_state
        [:id :int "PRIMARY KEY" "GENERATED ALWAYS AS IDENTITY"]
        [:t "CHAR(26) FOR BIT DATA"]
        [:state "varchar(1024)"])))
    (mpd/player-state mpd)
    (exec
     (fn []
       (while true
         (let [state (deref (:last-known-state mpd))]
           (when state
             (jdbc/insert! (:con con)
                           :idle_state
                           {:t (now-as-bytes)
                            :state (name state)})))
         (Thread/sleep (* 60 1000)))))
    (exec
     (fn []
       (Thread/sleep 1000)
       (while true
         (let [state (deref (:last-known-state mpd))
               now (BitSet/valueOf (now-as-bytes))
               db (for [{:keys [t state]}
                        (jdbc/query (:con con)
                                    "SELECT * FROM idle_state")]
                    [(BitSet/valueOf t) (keyword state)])
               m (make-model db)
               m2 (make-model2 db)]
           (log/trace "db %"
                      (normalize (frequencies (map second db))))
           (log/trace "m1"
                      (normalize (apply merge-with +
                                        (for [i (range (.size now))]
                                          (get m [i (.get now i)])))))
           (log/trace "m2"
                      (normalize (apply merge-with (fnil + 0 0)
                                        (for [i (range (.size now))]
                                          (get m2 [i (.get now i)] {})))))
           (log/trace "m2 / db %"
                      (let [db% (normalize (frequencies (map second db)))
                            m2 (normalize (apply merge-with (fnil + 0 0)
                                                 (for [i (range (.size now))]
                                                   (get m2 [i (.get now i)] {}))))]
                        (normalize (into {} (for [k (distinct (concat (keys db%)
                                                                      (keys m2)))]
                                              [k (/ (get m2 k 0) (get db% k 0))])))))
           (when state
             (log/trace "current state" state)
             (log/trace "known states" (count db))
             (when-let [[x] (seq (for [pct (range 0 100 5)
                                       :let [f (frequencies
                                                (for [[t state] db
                                                      :let [missc (.cardinality
                                                                   (doto (.clone now)
                                                                     (.xor t)))
                                                            missp (* 100.0 (/ missc (.size now)))]
                                                      :when (<= missp pct)]
                                                  state))
                                             state-guess (first (apply max-key second [nil 0] (seq f)))]
                                       :when (= state state-guess)]
                                   {:f f :pct (double pct)}))]
               (log/trace x))))
         (Thread/sleep (* 30 60 1000)))))
    this)
  (stop [this]
    this))
