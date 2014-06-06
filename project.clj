(defproject stoic "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]

                 [org.clojure/tools.logging "0.2.6"]

                 [zookeeper-clj "0.9.1" :exclusions [org.apache.zookeeper/zookeeper
                                                     log4j]]

                 [org.apache.zookeeper/zookeeper "3.4.5" :exclusions [commons-codec
                                                                      com.sun.jmx/jmxri
                                                                      com.sun.jdmk/jmxtools
                                                                      javax.jms/jms
                                                                      org.slf4j/slf4j-log4j12
                                                                      log4j]]
                 [environ "0.4.0"]

                 [com.stuartsierra/component "0.2.1"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [curator "0.0.2"]])
