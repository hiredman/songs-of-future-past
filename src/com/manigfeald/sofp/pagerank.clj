(ns com.manigfeald.sofp.pagerank
  (:require [com.stuartsierra.component :as component]
            [com.manigfeald.sofp.protocols :as p]
            [clojure.tools.logging :as log]
            [clojure.core.matrix :refer [rows esum array emul mmul mul inverse row-count div identity-matrix sub
                                         transpose]]))

(defn pagerank [graph]
  (let [click-through 0.85]
    (-> (p/adjacency-matrix graph)
        (update-in [:matrix] (fn [matrix]
                               (array (for [row (rows matrix)
                                            :let [sum (esum row)]]
                                        (if (zero? sum)
                                          row
                                          (emul row (/ 1 (esum row))))))))
        (update-in [:matrix] transpose)
        (update-in [:matrix]
                   (fn [inbound]
                     (mmul (inverse (sub (identity-matrix (row-count inbound))
                                         (mul click-through inbound)))
                           (mul (- 1.0 click-through)
                                (div (repeat (row-count inbound) 1000000)
                                     (esum (repeat (row-count inbound) 1000000)))))))
        ((fn [{:keys [matrix columns]}]
           (sort-by (comp (partial - 0) second) (map vector columns matrix))))
        ((fn [ids]
           (for [[id rank] ids]
             [(p/get-label graph id) rank]))))))

(defrecord PageRanker [db sig]
  component/Lifecycle
  (start [this]
    (when sig
      (sig "USR2"
           (fn []
             (log/info "Page ranking...")
             (log/info "====================")
             (doseq [[label rank] (take 20 (this))]
               (log/info label rank))
             (log/info "===================="))))
    (log/trace "starting page ranker")
    this)
  (stop [this]
    this)
  clojure.lang.IFn
  (invoke [_]
    (pagerank db)))
