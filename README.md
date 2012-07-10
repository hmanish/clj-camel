# clj-camel

clj-camel provides a thin wrapper over the Apache Camel Java DSL. The routes are wrtten as clojure vectors
where the first element is the keyword corresponding to the camel dsl (converted to clojure convention,
e.g onException will be represented by  :on-exception) and subsequent elements are the
parameters to the dsl element. For paramterless dsl element, the keyword can be specified
with or without the vector.

Expressing the routes as simple clojure vectors allows you to manipulate and compose routes with
all the goodness of functional programming.

## Usage

    (require '[clj-camel.core :as c])

    (defn test-bean [exchange body]
      (even? body))

    (def error-handler 
      [[:error-handler (c/defaultErrorHandler)]
       [:log-stack-trace true]
       [:log-retry-stack-trace true]
       [:log-handled true]
       [:log-exhausted true]
       [:retry-attempted-log-level LoggingLevel/WARN]
       [:redelivery-delay 1000]
       [:maximum-redeliveries 3]])

    (def test-routes
      [
       [[:from "direct:test-route-error"]
        [:log "error occurred: ${exception}"]]
       
       [[:from "direst:test-route-2"]
        [:to "file://test"]]
    
       [[:from "direct:test-route-1"]
        [:route-id "test-route-1"]
        [:on-exception Exception]
        [:redelivery-delay 30000]
        [:handled true]
        [:to "direct:test-route-error"]
        [:end]
        [:set-header :exchange][:exchange]
        [:bean-ref "test-bean" "invoke(${header.:exchange}, ${body})"]
        [:to "direct:test-route-2"]]
       ])

    (defn start-camel-context []
      (let [r (SimpleRegistry.)
            ctx (doto (DefaultCamelContext. r))]
        (.put r "test-bean" test-bean)
        (c/add-routes ctx (cons make-error-handler make-test-routes))
        (.start ctx)
        ctx))

## License

Copyright (c) 2012-2013 Manish Handa and released under an MIT license.

