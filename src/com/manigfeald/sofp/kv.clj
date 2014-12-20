(ns com.manigfeald.sofp.kv
  (:refer-clojure :exclude [contains?
                            read])
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]))

(defn contains? [store key]
  (not (zero? (:c (first (jdbc/query (:con store)
                                     ["SELECT COUNT(*) AS c FROM key_value_store WHERE key = ?" key]))))))

(defn write [store key value]
  (if (contains? store key)
    (jdbc/update! (:con store) :key_value_store {:value value} ["key = ?" key])
    (jdbc/insert! (:con store) :key_value_store {:key key :value value})))

(defn read [store key]
  (first (map :value (jdbc/query (:con store) ["SELECT value FROM key_value_store WHERE key = ?" key]))))

(defrecord KeyValueStore [con]
  component/Lifecycle
  (start [this]
    (log/info "start key value store")
    (when (:first-time? con)
      (jdbc/db-do-commands
       (:con con)
       (jdbc/create-table-ddl
        :key_value_store
        [:id :int "PRIMARY KEY" "GENERATED ALWAYS AS IDENTITY"]
        [:key "CHAR(32672) FOR BIT DATA"]
        [:value "CHAR(32672) FOR BIT DATA"])))
    (assoc this
      :con (:con con)))
  (stop [this]
    this))
