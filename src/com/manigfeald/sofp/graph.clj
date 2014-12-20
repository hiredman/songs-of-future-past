(ns com.manigfeald.sofp.graph
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as j]
            [clojure.tools.logging :as log]
            [com.manigfeald.sofp.protocols :as p]
            [clojure.core.protocols :as cp]
            [loom.graph :as g]
            [loom.attr :as a]
            [com.manigfeald.graph :as dg])
  (:import (java.util UUID)))

(defn weighted-walk [this src]
  (p/transact
   this
   (fn [graph]
     [(when-let [src (first (dg/attribute-node-search graph :text/label src))]
        (when-let [targets (for [succ (g/successors graph src)]
                             [succ (g/weight graph src succ)])]
          (let [sum (apply + 0 (map second targets))
                choice (rand-int sum)]
            (loop [base 0
                   [[target n] & targets] targets]
              (when target
                (if (> (+ base n) choice (dec base))
                  (a/attr graph target :text/label)
                  (recur (+ base (long n)) targets)))))))
      graph])))

(defn adjacency-matrix* [this]
  {:post [(seq (for [x (:matrix %) y x] (pos? y)))]}
  (p/transact
   this
   (fn [graph]
     [(let [start (System/currentTimeMillis)
            nodes (vec (g/nodes graph))]
        (try
          {:columns nodes
           :matrix
           (vec (for [src-node nodes]
                  (vec (for [target-node nodes]
                         (or (g/weight graph src-node target-node) 0)))))}
          (finally
            (log/trace "adjacency-matrix columns" (- (System/currentTimeMillis) start)))))
      graph])))

(defn increment-edge [this src-label target-label]
  {:pre [(string? src-label)
         (string? target-label)]}
  (p/transact
   this
   (fn [g]
     (log/trace src-label "->" target-label)
     (let [[src] (dg/attribute-node-search g :text/label src-label)
           [target] (dg/attribute-node-search g :text/label target-label)
           [g src] (if src
                     [g src]
                     (let [i (UUID/randomUUID)]
                       [(-> (g/add-nodes g i)
                            (a/add-attr i :text/label src-label)) i]))
           [g target] (if target
                        [g target]
                        (let [i (UUID/randomUUID)]
                          [(-> (g/add-nodes g i)
                               (a/add-attr i :text/label target-label))
                           i]))
           weight (or (g/weight g src target) 0)]
       (log/trace "src" src-label src)
       (log/trace "target" target-label target)
       [nil (-> g
                (g/remove-edges [src target])
                (g/add-edges [src target (inc weight)]))]))))

(defrecord DerbyDatabase [con g]
  component/Lifecycle
  (start [this]
    (log/info "starting derby")
    (assoc this
      :con (:con con)))
  (stop [this]
    this)
  p/GraphStore
  (transact [this fun]
    ((:g this) fun))
  (adjacency-matrix [this]
    (adjacency-matrix* this))
  (get-label [gs node-id]
    (with-open [g (deref g)]
      (a/attr g node-id :text/label))))

(defrecord Graph [con]
  component/Lifecycle
  (start [this]
    (log/info "starting graph")
    (let [con (:con con)
          store (dg/graph-store
                 con
                 {:named-graph "ng"
                  :graph "g"
                  :fragment "f"
                  :graph-fragments "gf"
                  :edge "e"
                  :node "n"
                  :attribute/text "t"
                  :attribute/double "d"})]
      (future
        (with-open [g (dg/read-only-view store "music")]
          (assert (pos? (count (g/nodes g))))
          (doseq [n (g/nodes g)]
            ;; (assert (a/attr g  n :text/label) n)
            (when-not (a/attr g n :text/label)
              (log/trace "removing" n)
              (dg/transact! store "music"
                            (fn [g]
                              [g (g/remove-nodes g n)]))))))
      (future
        (let [start (System/nanoTime)]
          (dg/gc store)
          (log/trace "gc time" (/ (- (System/nanoTime) start)
                                  1000000.0))))
      (doseq [[k t] (:config store)]
        (log/trace k (first (map :c (j/query con [(format "SELECT COUNT(*) AS c FROM %s" t)])))))
      (when (:first-time? con)
        (dg/create-tables! store))
      (assoc this
        :store store)))
  (stop [this]
    (.close ^java.sql.Connection (:connection (:con this)))
    this)
  clojure.lang.IFn
  (invoke [this fun]
    (dg/transact! (:store this) "music" fun))
  clojure.lang.IDeref
  (deref [this]
    (dg/read-only-view (:store this) "music")))

(defrecord DatabaseConnection [^java.io.File file]
  component/Lifecycle
  (start [this]
    (log/info "starting derby connection")
    (System/setProperty "derby.stream.error.file"
                        (str
                         (.getAbsolutePath (.getParentFile file))
                         java.io.File/separator
                         ".sofp.derby.log"))
    (let [first-time? (not (.exists file))
          con-params {:connection-uri (format "jdbc:derby:%s;create=true"
                                              (.getAbsolutePath file))}
          con (j/get-connection con-params)
          con (assoc con-params
                :connection con)]
      (assoc this
        :first-time? first-time?
        :con con)))
  (stop [this]
    (.close ^java.sql.Connection (:connection (:con this)))
    this)
  clojure.lang.IDeref
  (deref [this]
    (:con this)))
