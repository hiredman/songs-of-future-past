(defproject com.manigfeald/sofp "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.stuartsierra/component "0.2.1"]
                 [net.thejavashop/javampd "5.0.0"]
                 [org.apache.derby/derby "10.10.2.0"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [ch.qos.logback/logback-classic "1.1.1"]
                 [ch.qos.logback/logback-core "1.1.1"]
                 [org.clojure/tools.logging "0.3.0"]
                 [net.mikera/core.matrix "0.29.1"]
                 [aysylu/loom "0.5.0"]
                 [org.clojure/tools.nrepl "0.2.5"]
                 [com.manigfeald/graph "0.2.0"]]
  :main com.manigfeald.sofp
  :aot #{com.manigfeald.sofp})
