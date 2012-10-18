(def camel-version "2.9.2")

(defproject clj-camel "1.0.0-RELEASE"
  :description "Clojure library wrapping the Apache Camel Java DSL"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.apache.camel/camel-core ~camel-version]
                 [org.clojure/tools.logging "0.2.3"]])