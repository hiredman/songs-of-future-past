(ns com.manigfeald.sofp.protocols)

(defprotocol GraphStore
  (adjacency-matrix [gs])
  (get-label [gs node-id])
  (transact [gs fun]))

;; function name cannot be wait because that will clash with Object's
;; wait method
(defprotocol AWait
  (-wait [thing]))

(extend-protocol AWait
  Object
  (-wait [thing] thing))
