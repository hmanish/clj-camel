(ns clj-camel.core
  "This library provides a thin wrapper over the Apache Camel Java
DSL. The routes are wrtten as clojure vectors where the first element
is the keyword corresponding to the camel dsl (converted to clojure
convention, e.g onException will be represented by  :on-exception)
and subsequent elements are the parameters to the dsl element. For
paramterless dsl element, the keyword can be specified with or
without the vector.

Expressing the routes as simple clojure vectors allows you to
manipulate and compose routes with all the goodness of functional
programming."

  (:import [org.apache.camel.model ProcessorDefinition MulticastDefinition ThreadsDefinition ChoiceDefinition
            TryDefinition RecipientListDefinition RoutingSlipDefinition DynamicRouterDefinition
            SamplingDefinition SplitDefinition ResequenceDefinition AggregateDefinition
            DelayDefinition ThrottleDefinition LoopDefinition OnExceptionDefinition RouteDefinition
            SortDefinition OnCompletionDefinition InterceptSendToEndpointDefinition InterceptDefinition
            IdempotentConsumerDefinition]
           [org.apache.camel.model.language HeaderExpression MethodCallExpression PropertyExpression]
           [org.apache.camel.builder Builder RouteBuilder ValueBuilder SimpleBuilder
            DefaultErrorHandlerBuilder NoErrorHandlerBuilder LoggingErrorHandlerBuilder
            DeadLetterChannelBuilder ExpressionClause DataFormatClause]
           [org.apache.camel.builder.xml XPathBuilder]
           [org.apache.camel Endpoint NoSuchEndpointException  LoggingLevel util.ObjectHelper]
           [org.slf4j Logger]
           [com.bpk.cljcamel RouteBuilderImpl])
  (:require [clojure.tools.logging :as l])
  (:refer-clojure :exclude [bean error-handler]))

(defmacro setp [obj method val]
  `(when (not (nil? ~val))
     (. ~obj ~method ~val)))

(defn derive-all [tgt parents]
  (if (sequential? parents)
    (doseq [p parents]
      (derive tgt p))
    (derive tgt parents)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;         BuilderSupport DSL methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn header [name]
  (ValueBuilder. (HeaderExpression. (str name))))

(defn property [name]
  (ValueBuilder. (PropertyExpression. name)))

(defn body
  ([] (Builder/body))
  ([type] (Builder/bodyAs type)))

(defn outBody
  ([] (Builder/outBody))
  ([type] (Builder/outBodyAs type)))

(defn faultBody
  ([] (Builder/faultBody))
  ([type] (Builder/faultBodyAs type)))
                             

(defn systemProperty
  ([name] (Builder/systemProperty name))
  ([name default] (Builder/systemProperty name default)))

(defn constant [val]
  (Builder/constant val))

(defn simple
  ([value] (SimpleBuilder/simple value))
  ([value type] (SimpleBuilder/simple value type)))

(defn xpath [value]
  (XPathBuilder/xpath value))

(defn bean
  ([beanOrBeanRef] (bean beanOrBeanRef nil))
  ([beanOrBeanRef method] (ValueBuilder. (MethodCallExpression. beanOrBeanRef method))))

(defn bean-by-type
  ([type] (ValueBuilder. (MethodCallExpression. type)))
  ([type method] (ValueBuilder. (MethodCallExpression. type method))))

(defn sendTo [uri]
  (Builder/sendTo uri))

(defn regexReplaceAll [expression-content regex string-replacement]
   (Builder/regexReplaceAll expression-content regex string-replacement))

(defn regexReplaceAll-expression [expression-content regex expression-replacement]
   (Builder/regexReplaceAll expression-content regex expression-replacement))

(defn exceptionMessage []
  (Builder/exceptionMessage))

(defn endpoint
  ([ctx uri] 
     (ObjectHelper/notNull uri "uri")
     (if-let [endpoint (.getEndpoint ctx uri)]
       endpoint
       (throw (NoSuchEndpointException. uri))))
  ([ctx uri type]
     (ObjectHelper/notNull uri "uri")
     (if-let [endpoint (.getEndpoint ctx uri type)]
       endpoint
       (throw (NoSuchEndpointException. uri)))))

(defn endpoints [uri-list]
  (java.util.ArrayList. (map endpoint uri-list)))

(defn defaultErrorHandler []
  (DefaultErrorHandlerBuilder.))

(defn noErrorHandler []
  (NoErrorHandlerBuilder.))

(defn loggingErrorHandler
  ([] (LoggingErrorHandlerBuilder.))
  ([log] (LoggingErrorHandlerBuilder. log))
  ([log log-level] (LoggingErrorHandlerBuilder. log log-level)))

(defn  deadLetterChannel
  ([channel]
     (DeadLetterChannelBuilder. channel)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                 MULTIMETHODS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti handle-spec (fn [bldr key & _]
                           (if (coll? key)
                             [(class bldr) (first key)]
                             [(class bldr) key])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              EXPRESSION CLAUSE SUPPORT
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod handle-spec [ExpressionClause :expression] [ec _ expression]
  (.expression ec expression))

(defmethod handle-spec [ExpressionClause :constant] [ec _ val]
  (.constant ec val))

(defmethod handle-spec [ExpressionClause :exchange] [ec _]
  (.exchange ec))

(defmethod handle-spec [ExpressionClause :in-message] [ec _]
  (.inMessage ec))

(defmethod handle-spec [ExpressionClause :out-message] [ec _]
  (.outMessage ec))

(defmethod handle-spec [ExpressionClause :body]
  ([ec _] (.body ec))
  ([ec _ type] (.body ec type)))

(defmethod handle-spec [ExpressionClause :out-body]
  ([ec _] (.outBody ec))
  ([ec _ type] (.outBody ec type)))

(defmethod handle-spec [ExpressionClause :header] [ec _ name]
  (.header ec (str name)))

(defmethod handle-spec [ExpressionClause :headers] [ec _]
  (.headers ec))

(defmethod handle-spec [ExpressionClause :out-header] [ec _ name]
  (.outHeader ec (str name)))

(defmethod handle-spec [ExpressionClause :out-headers] [ec _]
  (.outHeaders ec))

(defmethod handle-spec [ExpressionClause :attachments] [ec _]
  (.attachments ec))

(defmethod handle-spec [ExpressionClause :property] [ec _ name]
  (.property ec (str name)))

(defmethod handle-spec [ExpressionClause :properties] [ec _]
  (.properties ec))

(defmethod handle-spec [ExpressionClause :method]
  ([ec _ bean-name-instance-or-class] (.method ec bean-name-instance-or-class))
  ([ec _ bean-name-instance-or-class method-name] (.method ec bean-name-instance-or-class method-name)))

(defmethod handle-spec [ExpressionClause :el] [ec _ text]
  (.el ec text))

(defmethod handle-spec [ExpressionClause :groovy] [ec _ text]
  (.groovy ec text))

(defmethod handle-spec [ExpressionClause :javascript] [ec _ text]
  (.javaScript ec text))

(defmethod handle-spec [ExpressionClause :jxpath] [ec _ text]
  (.jxpath ec text))

(defmethod handle-spec [ExpressionClause :ognl] [ec _ text]
  (.ognl ec text))

(defmethod handle-spec [ExpressionClause :mvel] [ec _ text]
  (.mvel ec text))

(defmethod handle-spec [ExpressionClause :php] [ec _ text]
  (.php ec text))

(defmethod handle-spec [ExpressionClause :python] [ec _ text]
  (.python ec text))

(defmethod handle-spec [ExpressionClause :ref] [ec _ text]
  (.ref ec text))

(defmethod handle-spec [ExpressionClause :ruby] [ec _ text]
  (.ruby ec text))

(defmethod handle-spec [ExpressionClause :sql] [ec _ text]
  (.sql ec text))

(defmethod handle-spec [ExpressionClause :spel] [ec _ text]
  (.spel ec text))

(defmethod handle-spec [ExpressionClause :simple]
  ([ec _ text] (.simple ec text))
  ([ec _ text type] (.simple ec text type)))

(defmethod handle-spec [ExpressionClause :tokenize]
  ([ec _ token] (.tokenize ec token))
  ([ec _ token header-name] (.tokenize ec token (str header-name)))
  ([ec _ token header-name regex] (.tokenize ec token (str header-name) regex)))

(defmethod handle-spec [ExpressionClause :tokenize-pair]
  ([ec _ start-token end-token] (.tokenizePair ec start-token end-token))
  ([ec _ start-token end-token include-tokens] (.tokenize ec start-token end-token include-tokens)))

(defmethod handle-spec [ExpressionClause :tokenize-xml]
  ([ec _ tag-name] (.tokenizeXML ec tag-name))
  ([ec _ tag-name inherit-namespace-tag-name] (.tokenizeXML ec tag-name inherit-namespace-tag-name)))

(defmethod handle-spec [ExpressionClause :xpath]
  ([ec _ text] (.xpath ec text))
  ([ec _ text type-or-namespace] (.xpath ec text type-or-namespace))
  ([ec _ text type namespaces] (.xpath ec text type namespaces)))

(defmethod handle-spec [ExpressionClause :xquery]
  ([ec _ text] (.xquery ec text))
  ([ec _ text type-or-namespace] (.xquery ec text type-or-namespace))
  ([ec _ text type namespaces] (.xquery ec text type namespaces)))


(defmethod handle-spec [ExpressionClause :language] [ec _ lang expression]
  (.language ec lang expression))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :to]
  ([rd _ dest exchange-pattern] (. rd to exchange-pattern dest))
  ([rd _ dest] (. rd to dest)))


(defmethod handle-spec [ProcessorDefinition :placeholder] [rd _ option key]
  (. rd placeholder option key))

(defmethod handle-spec [ProcessorDefinition :attribute] [rd _ name value]
  (. rd attribute name value))

(defmethod handle-spec [ProcessorDefinition :set-exchange-pattern] [rd _ exchange-pattern]
  (. rd setExchangePattern exchange-pattern))

(defmethod handle-spec [ProcessorDefinition :in-only] [rd _ uris]
  (. rd inOnly uris))

(defmethod handle-spec [ProcessorDefinition :in-out] [rd _ uris]
  (. rd inOut uris))

(defmethod handle-spec [ProcessorDefinition :id] [rd _ id]
  (. rd id id))

(defmethod handle-spec [ProcessorDefinition :route-id] [rd _ route-id]
  (. rd routeId route-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              COMMON METHODS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [::agg-strategy-aware :aggregation-strategy] [rd _ agg-strategy]
  (.aggregationStrategy rd agg-strategy))

(defmethod handle-spec [::agg-strategy-aware :aggregation-strategy-ref] [rd _ agg-strategy-ref]
  (.aggregationStrategyRef rd  agg-strategy-ref))

(defmethod handle-spec [::parallel-processing-aware :parallel-processing] [rd _]
  (. rd parallelProcessing))

(defmethod handle-spec [::executor-service-aware :executor-service] [rd _ executor-service]
  (.executorService rd executor-service))

(defmethod handle-spec [::executor-service-aware :executor-service-ref] [rd _ executor-service-ref]
  (.executorServiceRef rd executor-service-ref))

(defmethod handle-spec [::streaming-aware :streaming] [rd _]
  (.streaming rd))

(defmethod handle-spec [::stop-on-exception-aware :stop-on-exception] [rd _]
  (.stopOnException rd))

(defmethod handle-spec [::timeout-aware :timeout] [rd _ timeout] 
  (.timeout rd timeout))

(defmethod handle-spec [::on-prepare-aware :on-prepare] [rd _ on-prepare] 
  (.onPrepare rd on-prepare))

(defmethod handle-spec [::on-prepare-aware :on-prepare-ref] [rd _ on-prepare-ref] 
  (.onPrepareRef rd on-prepare-ref))

(defmethod handle-spec [::share-unit-of-work-aware :share-unit-of-work] [rd _] 
  (.shareUnitOfWork rd))

(defmethod handle-spec [::caller-runs-when-rejected-aware :caller-runs-when-rejected] [rd _]
  (.callerRunsWhenRejected rd))

(defmethod handle-spec [::async-delayed-aware :async-delayed] [rd _]
  (.asyncDelayed rd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                       MULTICAST DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(derive-all MulticastDefinition [::agg-strategy-aware ::parallel-processing-aware ::executor-service-aware
                                 ::streaming-aware ::stop-on-exception-aware ::timeout-aware ::on-prepare-aware
                                 ::share-unit-of-work-aware])

(defmethod handle-spec [ProcessorDefinition :multicast] [rd _]
  (.multicast rd ))
  
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :pipeline] [rd _ uris]
  (. rd pipeline uris))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         THREADS DSL
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(derive-all ThreadsDefinition [::executor-service-aware ::caller-runs-when-rejected-aware])

(defmethod handle-spec [ThreadsDefinition :pool-size] [td _ pool-size]
  (.poolSize td pool-size))

(defmethod handle-spec [ThreadsDefinition :max-pool-size] [td _ max-pool-size]
  (.maxPoolSize td max-pool-size))

(defmethod handle-spec [ThreadsDefinition :keep-alive-time] [td _ keep-alive-time]
  (.keepAliveTime td keep-alive-time))

(defmethod handle-spec [ThreadsDefinition :time-unit] [td _ time-unit]
  (.timeUnit td time-unit))

(defmethod handle-spec [ThreadsDefinition :max-queue-size] [td _ max-queue-size]
  (.maxQueueSize td max-queue-size))

(defmethod handle-spec [ThreadsDefinition :thread-name] [td _ thread-name]
  (.threadName td thread-name))

(defmethod handle-spec [ThreadsDefinition :rejected-policy] [td _ rejected-policy]
  (.rejectedPolicy td rejected-policy))

(defmethod handle-spec [ProcessorDefinition :threads] [rd _]
  (. rd threads))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :end] [rd _]
  (. rd end))

(defmethod handle-spec [ProcessorDefinition :end-parent] [rd _]
  (. rd endParent))

(defmethod handle-spec [ProcessorDefinition :end-choice] [rd _]
  (. rd endChoice))

(defmethod handle-spec [ProcessorDefinition :end-do-try] [rd _]
  (. rd endDoTry))

(defmethod handle-spec [ProcessorDefinition :idempotent-consumer]
  ([rd _ message-id-expression] (. rd idempotentConsumer message-id-expression))
  ([rd _ message-id-expression idempotent-repository] (. rd idempotentConsumer message-id-expression idempotent-repository)))
  
(defmethod handle-spec [ProcessorDefinition :filter]
  ([rd _ pred] (. rd filter pred))
  ([rd _] (. rd filter)))

(defmethod handle-spec [ProcessorDefinition :validate] [rd _ expression]
  (. rd validate expression))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         IDEMPOTENT CONSUMER DSL
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [IdempotentConsumerDefinition :message-id-repository-ref]
  [cd _ ref] (.messageIdRepositoryRef cd ref))

(defmethod handle-spec [IdempotentConsumerDefinition :message-id-repository]
  [cd _ repo] (.messageIdRepository cd repo))

(defmethod handle-spec [IdempotentConsumerDefinition :eager]
  [cd _ is-eager] (.eager cd is-eager))

(defmethod handle-spec [IdempotentConsumerDefinition :remove-on-failure]
  [cd _ is-remove] (.removeOnFailure cd is-remove))

(defmethod handle-spec [IdempotentConsumerDefinition :skip-duplicate]
  [cd _ is-skip-duplicate] (.skipDuplicate cd is-skip-duplicate))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                LOADBALANCE DEF
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

                 ;; TO BE IMPLEMENTED

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :log]
  ([rd _ message] (. rd log message))
  ([rd _ logging-level message] (. rd log logging-level message))
  ([rd _ logging-level log-name message] (. rd log logging-level log-name message))
  ([rd _ looging-level log-name marker message] (. rd log looging-level log-name marker message)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                  CHOICE DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod handle-spec [ProcessorDefinition :choice] [rd _ & when-clauses]
  (.choice rd ))

(defmethod handle-spec [ChoiceDefinition :when]
  ([cd _] (.when cd))
  ([cd _ pred] (.when cd pred)))

(defmethod handle-spec [ChoiceDefinition :otherwise] [cd _]
  (.otherwise cd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              TRY CATCH FINALLY
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod handle-spec [ProcessorDefinition :do-try] [rd _]
  (.doTry rd))

(defmethod handle-spec [TryDefinition :do-catch] [rd _ & exceptions]
  (.doCatch rd (into-array exceptions)))

(defmethod handle-spec [TryDefinition :do-finally] [rd _]
  (.doFinally rd))

(defmethod handle-spec [TryDefinition :on-when] [rd _ predicate]
  (.onWhen rd predicate))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              RECIPIENT LIST
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(derive-all RecipientListDefinition [::agg-strategy-aware ::parallel-processing-aware ::executor-service-aware
                                     ::streaming-aware ::stop-on-exception-aware ::timeout-aware ::on-prepare-aware
                                     ::share-unit-of-work-aware])

(defmethod handle-spec [ProcessorDefinition :recipient-list]
  ([rd _] (.recipientList rd))
  ([rd _ recipients] (.recipientList rd recipients))
  ([rd _ recipients delimiter] (.recipientList rd recipients delimiter)))

(defmethod handle-spec [RecipientListDefinition :ignore-invalid-endpoints] [rd _]
  (.ignoreInvalidEndpoints rd))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              ROUTING SLIP
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :routing-slip]
  ([rd _] (.routingSlip rd))
  ([rd _ expression] (.routingSlip rd expression))
  ([rd _ expression delimiter] (.routingList rd expression delimiter)))

(defmethod handle-spec [RoutingSlipDefinition :ignore-invalid-endpoints] [rd _]
  (.ignoreInvalidEndpoints rd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              DYNAMIC ROUTER
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :dynamic-router]
  ([rd _] (.dynamicRouter rd))
  ([rd _ expression] (.dynamicRouter rd expression)))

(defmethod handle-spec [DynamicRouterDefinition :ignore-invalid-endpoints] [rd _]
  (.ignoreInvalidEndpoints rd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; SAMPLE DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :sample]
  ([rd _] (.sample rd))
  ([rd _ sample-period time-unit] (.sample rd sample-period time-unit))
  ([rd _ message-frequency] (.sample rd message-frequency)))

(defmethod handle-spec [SamplingDefinition :sample-message-frequency] [sd _ message-frequency]
  (.messageFrequency sd message-frequency))

(defmethod handle-spec [SamplingDefinition :sample-period] [sd _ sample-period]
  (.samplePeriod sd sample-period))

(defmethod handle-spec [SamplingDefinition :time-units] [sd _ time-units]
  (.timeUnits sd time-units))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              SPLIT DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(derive-all SplitDefinition [::agg-strategy-aware ::parallel-processing-aware ::executor-service-aware
                             ::streaming-aware ::stop-on-exception-aware ::timeout-aware ::on-prepare-aware
                             ::share-unit-of-work-aware])

(defmethod handle-spec [ProcessorDefinition :split]
  ([rd _] (.split rd))
  ([rd _ expression] (.split rd expression))
  ([rd _ expression agg-strategy] (.split rd expression agg-strategy)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;           Resequence
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(derive ResequenceDefinition ::timeout-aware)

(defmethod handle-spec [ProcessorDefinition :resequence]
  ([rd _] (.resequence rd))
  ([rd _ expression] (.resequence rd expression)))

(defmethod handle-spec [ResequenceDefinition :stream]
  ([rd _] (.stream rd))
  ([rd _ config] (.stream rd config)))
  
(defmethod handle-spec [ResequenceDefinition :batch]
  ([rd _] (.batch rd))
  ([rd _ config] (.batch rd config)))

(defmethod handle-spec [ResequenceDefinition :size] [rd _ batch-size]
  (.size rd batch-size))

(defmethod handle-spec [ResequenceDefinition :capacity] [rd _ capacity]
  (.capacity rd capacity))

(defmethod handle-spec [ResequenceDefinition :allow-duplicates] [rd _]
  (.allowDuplicates rd))

(defmethod handle-spec [ResequenceDefinition :reverse] [rd _]
  (.reverse rd))

(defmethod handle-spec [ResequenceDefinition :ignore-invalid-exchanges] [rd _]
  (.ignoreInvalidExchanges rd))

(defmethod handle-spec [ResequenceDefinition :comparator] [rd _ comparator]
  (.comparator rd comparator))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              AGGREGATE DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(derive-all AggregateDefinition [::agg-strategy-aware ::parallel-processing-aware ::executor-service-aware
                                   ::streaming-aware ::stop-on-exception-aware ::timeout-aware ::on-prepare-aware
                                   ::share-unit-of-work-aware])

(defmethod handle-spec [ProcessorDefinition :aggregate]
  ([rd _] (.aggregate rd))
  ([rd _ expression-or-strategy] (.aggregate rd expression-or-strategy))
  ([rd _ expression agg-strategy] (.aggregate rd expression agg-strategy)))

(defmethod handle-spec [AggregateDefinition :eager-check-completion] [asd _]
  (.eagerCheckCompletion asd))

(defmethod handle-spec [AggregateDefinition :ignore-invalid-correlation-keys] [asd _]
  (.ignoreInvalidCorrelationKeys asd))

(defmethod handle-spec [AggregateDefinition :close-correlation-key-on-completion] [asd _ capacity]
  (.closeCorrelationKeyOnCompletion asd capacity))

(defmethod handle-spec [AggregateDefinition :discard-on-completion-timeout] [asd _]
  (.discardOnCompletionTimeout asd))

(defmethod handle-spec [AggregateDefinition :completion-from-batch-consumer] [asd _]
  (.completionFromBatchConsumer asd))

(defmethod handle-spec [AggregateDefinition :completion-size] [asd _ size]
  (.completionSize asd size))

(defmethod handle-spec [AggregateDefinition :completion-interval] [asd _ interval]
  (.completionInterval asd interval))

(defmethod handle-spec [AggregateDefinition :completion-timeout] [asd _ timeout]
  (.completionTimeout asd timeout))

(defmethod handle-spec [AggregateDefinition :agg-repository] [asd _ repo]
  (.aggregationRepository asd repo))

(defmethod handle-spec [AggregateDefinition :agg-repository-ref] [asd _ repo-ref]
  (.aggregationRepositoryRef asd repo-ref))

(defmethod handle-spec [AggregateDefinition :group-exchanges] [asd _]
  (.group-exchanges asd))

(defmethod handle-spec [AggregateDefinition :completion-predicate] [asd _ pred]
  (.completionPredicate asd pred))

(defmethod handle-spec [AggregateDefinition :force-completion-on-stop] [asd _]
  (.forceCompletionOnStop asd))

(defmethod handle-spec [AggregateDefinition :timeout-checker-executor-service] [asd _ svc]
  (.timeoutCheckerExecutorService asd svc))

(defmethod handle-spec [AggregateDefinition :timeout-checker-executor-service-ref] [asd _ svc-ref]
  (.timeoutCheckerExecutorServiceRef asd svc-ref))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                DELAY DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(derive-all DelayDefinition [::executor-service-aware ::caller-runs-when-rejected-aware ::async-delayed-aware])
                             
(defmethod handle-spec [ProcessorDefinition :throttle] [rd _ max-request-count]
  (.throttle rd max-request-count))

(defmethod handle-spec [ThrottleDefinition :time-period-millis] [dd _ time]
  (.timePeriodMillis dd time))

(defmethod handle-spec [ThrottleDefinition :maximum-requests-per-period] [dd _ max-requests]
  (.maximumRequestsPerPeriod dd max-requests))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                THROTTLE DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(derive-all ThrottleDefinition [::executor-service-aware ::caller-runs-when-rejected-aware ::async-delayed-aware])
                             
(defmethod handle-spec [ProcessorDefinition :delay]
  ([rd _] (.delay rd))
  ([rd _ expression-or-time] (.delay rd expression-or-time)))

(defmethod handle-spec [DelayDefinition :delay-time] [dd _ time]
  (.delayTime dd time))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                LOOP DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :loop]
  ([rd _] (.loop rd))
  ([rd _ count] (.loop rd count)))

(defmethod handle-spec [LoopDefinition :copy] [dd _]
  (.copy dd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :throw-exception] [rd _ e]
  (.throwException rd e))

(defmethod handle-spec [ProcessorDefinition :mark-rollback-only] [rd _]
  (.markRollbackOnly rd))

(defmethod handle-spec [ProcessorDefinition :mark-rollback-only-last] [rd _]
  (.markRollbackOnlyLast rd))

(defmethod handle-spec [ProcessorDefinition :rollback]
  ([rd _] (.rollback rd))
  ([rd _ message] (.rollback rd message)))

(defmethod handle-spec [ProcessorDefinition :wire-tap] [rd _ uri]
  (.wireTap rd uri))

(defmethod handle-spec [ProcessorDefinition :stop] [rd _]
  (.stop rd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                ONEXCEPTION DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :on-exception] [rd _ exceptions]
  (.onException rd exceptions))

(defmethod handle-spec [OnExceptionDefinition :redelivery-delay] [ed _ delay]
  (.redeliveryDelay ed delay))

(defmethod handle-spec [OnExceptionDefinition :maximum-redeliveries] [ed _ max]
  (.maximumRedeliveries ed max))

(defmethod handle-spec [OnExceptionDefinition :maximum-redelivery-delay] [ed _ max]
  (.maximumRedeliveryDelay ed max))

(defmethod handle-spec [OnExceptionDefinition :use-exponential-back-off] [ed _]
  (.useExponentialBackOff ed))

(defmethod handle-spec [OnExceptionDefinition :back-off-multiplier] [ed _ m]
  (.backOffMultiplier ed m))

(defmethod handle-spec [OnExceptionDefinition :use-collision-avoidance] [ed _]
  (.useCollisionAvoidance ed))

(defmethod handle-spec [OnExceptionDefinition :retry-exhausted-log-level] [ed _ level]
  (.retryExhaustedLogLevel ed level))

(defmethod handle-spec [OnExceptionDefinition :retry-attempted-log-level] [ed _ level]
  (.retryAttemptedLogLevel ed level))


(defmethod handle-spec [OnExceptionDefinition :log-stack-trace] [ed _ y-or-n]
  (.logStackTrace ed y-or-n))

(defmethod handle-spec [OnExceptionDefinition :log-retry-stack-trace] [ed _ y-or-n]
  (.logRetryStackTrace ed y-or-n))

(defmethod handle-spec [OnExceptionDefinition :log-handled] [ed _ y-or-n]
  (.logHandled ed y-or-n))

(defmethod handle-spec [OnExceptionDefinition :log-continued] [ed _ y-or-n]
  (.logContinued ed y-or-n))

(defmethod handle-spec [OnExceptionDefinition :log-exhausted] [ed _ y-or-n]
  (.logExhausted ed y-or-n))

(defmethod handle-spec [OnExceptionDefinition :log-retry-attempted] [ed _ y-or-n]
  (.logRetryAttempted ed y-or-n))

(defmethod handle-spec [OnExceptionDefinition :handled] [ed _ y-or-n]
  (.handled ed y-or-n))

(defmethod handle-spec [OnExceptionDefinition :continued] [ed _ y-or-n]
  (.continued ed y-or-n))

(defmethod handle-spec [OnExceptionDefinition :async-delayed-redelivery] [ed _]
  (.asyncDelayedRedelivery ed))

(defmethod handle-spec [OnExceptionDefinition :use-original-message] [ed _]
  (.useOriginalMessage ed))

(defmethod handle-spec [OnExceptionDefinition :on-when] [ed _ pred] 
  (.onWhen ed pred))

(defmethod handle-spec [OnExceptionDefinition :redelivery-policy-ref] [ed _ ref] 
  (.redeliveryPolicyRef ed ref))

(defmethod handle-spec [OnExceptionDefinition :delay-pattern] [ed _ pat] 
  (.delayPattern ed pat))

(defmethod handle-spec [OnExceptionDefinition :on-redelivery] [ed _ processor] 
  (.onRedelivery ed processor))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [ProcessorDefinition :policy] [rd _ p]
  (.policy rd p))

(defmethod handle-spec [ProcessorDefinition :transacted]
  ([rd _] (.transacted rd))
  ([rd _ policy-ref] (.transacted rd policy-ref)))

(defmethod handle-spec [ProcessorDefinition :process] [rd _ proc]
  (.process rd proc))

(defmethod handle-spec [ProcessorDefinition :process-ref] [rd _ proc]
  (.processRef rd proc))

(defmethod handle-spec [ProcessorDefinition :bean]
  ([rd _ bean] (.bean rd bean))
  ([rd _ bean method] (.bean rd bean method)))

(defmethod handle-spec [ProcessorDefinition :bean-ref]
  ([rd _ bean-ref] (.beanRef rd bean-ref))
  ([rd _ bean-ref method] (.beanRef rd bean-ref method)))

(defmethod handle-spec [ProcessorDefinition :set-body]
  ([rd _] (.setBody rd))
  ([rd _ expression] (.setBody rd expression)))

(defmethod handle-spec [ProcessorDefinition :transform]
  ([rd _] (.transform rd))
  ([rd _ expression] (.transform rd expression)))

(defmethod handle-spec [ProcessorDefinition :set-fault-body] [rd _ body]
  (.setFaultBody rd body))

(defmethod handle-spec [ProcessorDefinition :set-header]
  ([rd _ name] (.setHeader rd (str name)))
  ([rd _ name expression] (.setHeader rd (str name) expression)))

(defmethod handle-spec [ProcessorDefinition :set-property]
  ([rd _ name] (.setProperty rd name))
  ([rd _ name expression] (.setProperty rd name expression)))

(defmethod handle-spec [ProcessorDefinition :remove-header] [rd _ name]
  (.removeHeader rd (str name)))

(defmethod handle-spec [ProcessorDefinition :remove-headers]
  ([rd _ patterns] (.removeHeaders rd patterns))
  ([rd _ patterns exclude-patterns] (.removeHeaders rd patterns exclude-patterns)))

(defmethod handle-spec [ProcessorDefinition :remove-property] [rd _ name]
  (.removeProperty rd (str name)))

(defmethod handle-spec [ProcessorDefinition :convert-body-to]
  ([rd _ cls] (.convertBodyTo rd cls))
  ([rd _ cls char-set] (.convertBodyTo rd cls char-set)))

(defmethod handle-spec [ProcessorDefinition :sort]
  ([rd _] (.sort rd))
  ([rd _ expression] (.sort rd expression))
  ([rd _ expression comparator] (.sort rd expression comparator)))

(defmethod handle-spec [ProcessorDefinition :enrich]
  ([rd _ uri] (.enrich rd uri))
  ([rd _ uri agg-strategy] (.enrich rd uri agg-strategy)))

(defmethod handle-spec [ProcessorDefinition :enrich-ref] [rd _ uri agg-strategy-ref]
  (.enrichRef rd uri agg-strategy-ref))

(defmethod handle-spec [ProcessorDefinition :poll-enrich]
  ([rd _ uri] (.pollEnrich rd uri))
  ([rd _ uri agg-strategy-or-timeout] (.pollEnrich rd uri agg-strategy-or-timeout))
  ([rd _ uri timeout agg-strategy] (.pollEnrich rd uri timeout agg-strategy)))

(defmethod handle-spec [ProcessorDefinition :poll-enrich-ref] [rd _ uri timeout agg-strategy-ref]
  (.pollEnrichRef rd uri timeout agg-strategy-ref))

(defmethod handle-spec [ProcessorDefinition :inherit-error-handler] [rd _ y-or-n]
  (.inheritErrorHandler rd y-or-n))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              ONCOMPLETION DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(derive OnCompletionDefinition ::executor-service-aware)

(defmethod handle-spec [ProcessorDefinition :on-completion] [rd _]
  (.onCompletion rd))

(defmethod handle-spec [OnCompletionDefinition :on-complete-only] [rd _]
  (.onCompleteOnly rd))

(defmethod handle-spec [OnCompletionDefinition :on-failure-only] [rd _]
  (.onFailureOnly rd))

(defmethod handle-spec [OnCompletionDefinition :on-when] [rd _ pred]
  (.onWhen rd pred))

(defmethod handle-spec [OnCompletionDefinition :use-original-body] [rd _]
  (.useOriginalBody rd))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         DATAFORMAT DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod handle-spec [ProcessorDefinition :unmarshal]
  ([rd _] (.unmarshal rd))
  ([rd _ format-type] (.unmarshal rd format-type)))
  
(defmethod handle-spec [ProcessorDefinition :marshal]
  ([rd _] (.marshal rd))
  ([rd _ format-type] (.marshal rd format-type)))
  
(defmethod handle-spec [DataFormatClause :bindy] [rd _ bindy-type packages]
  (.bindy rd bindy-type packages))

(defmethod handle-spec [DataFormatClause :csv] [rd _]
  (.csv rd))

(defmethod handle-spec [DataFormatClause :custom] [rd _ ref]
  (.custom rd ref))

(defmethod handle-spec [DataFormatClause :castor]
  ([rd _] (.castor rd))
  ([rd _ mapping-file] (.castor rd mapping-file))
  ([rd _ mapping-file validation] (.castor rd mapping-file validation)))

(defmethod handle-spec [DataFormatClause :gzip] [rd _]
  (.gzip rd))

(defmethod handle-spec [DataFormatClause :hl7]
  ([rd _] (.hl7 rd))
  ([rd _ validate] (.hl7 rd validate)))

(defmethod handle-spec [DataFormatClause :pgp]
  ([rd _ key-file user-id] (.pgp key-file user-id))
  ([rd _ key-file user-id password] (.pgp key-file user-id password))
  ([rd _ key-file user-id password armored integrity] (.pgp key-file user-id password armored integrity)))

(defmethod handle-spec [DataFormatClause :jaxb]
  ([rd _] (.jaxb rd))
  ([rd _ context-path-or-pretty-print] (.jaxb rd context-path-or-pretty-print)))

(defmethod handle-spec [DataFormatClause :jixb]
  ([rd _] (.jixb rd))
  ([rd _ cls] (.jixb rd cls)))

(defmethod handle-spec [DataFormatClause :json]
  ([rd _] (.json rd))
  ([rd _ lib] (.json rd lib))
  ([rd _ lib cls] (.json rd lib cls)))

(defmethod handle-spec [DataFormatClause :protobuf]
  ([rd _] (.protobuf rd))
  ([rd _ instance-or-instance-name] (.protobuf rd instance-or-instance-name)))

(defmethod handle-spec [DataFormatClause :rss] [rd _]
  (.rss rd))

(defmethod handle-spec [DataFormatClause :serialization] [rd _]
  (.serialization rd))

(defmethod handle-spec [DataFormatClause :soapjaxb]
  ([rd _] (.soapjaxb rd))
  ([rd _ context-path] (.soapjaxb rd context-path))
  ([rd _ context-path elem-strategy-name-or-ref] (.soapjaxb rd context-path elem-strategy-name-or-ref)))
  
(defmethod handle-spec [DataFormatClause :string]
  ([rd _] (.string rd))
  ([rd _ char-set] (.string rd char-set)))

(defmethod handle-spec [DataFormatClause :syslog] [rd _]
  (.syslog rd))

(defmethod handle-spec [DataFormatClause :tidy-markup]
  ([rd _] (.tidyMarkup rd))
  ([rd _ cls] (.tidyMarkup rd cls)))

(defmethod handle-spec [DataFormatClause :xstream]
  ([rd _] (.xstream rd))
  ([rd _ encoding] (.xstream rd encoding)))

(defmethod handle-spec [DataFormatClause :xml-beans] [rd _]
  (.xmlBeans rd))

(defmethod handle-spec [DataFormatClause :zip]
  ([rd _] (.zip rd))
  ([rd _ compression-level] (.tidyMarkup rd compression-level)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                 FROM DEFINITIONS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [RouteBuilder :from] [rb _ endpoints]
  (.from rb endpoints))

(defmethod handle-spec [RouteBuilder :fromF] [rb _ uri args]
  (.from rb uri args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         ROUTEDEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [RouteDefinition :route-id] [rd _ route-id]
  (.routeId rd route-id))

(defmethod handle-spec [RouteDefinition :group] [rd _ name]
  (.group rd name))

(defmethod handle-spec [RouteDefinition :no-stream-caching] [rd _]
  (.noStreamCaching rd))

(defmethod handle-spec [RouteDefinition :stream-caching] [rd _]
  (.streamCaching rd))

(defmethod handle-spec [RouteDefinition :no-tracing] [rd _]
  (.noTracing rd))

(defmethod handle-spec [RouteDefinition :tracing] [rd _]
  (.tracing rd))

(defmethod handle-spec [RouteDefinition :no-handle-fault] [rd _]
  (.noHandleFault rd))

(defmethod handle-spec [RouteDefinition :handle-fault] [rd _]
  (.handleFault rd))

(defmethod handle-spec [RouteDefinition :no-delayer] [rd _]
  (.noDelayer rd))

(defmethod handle-spec [RouteDefinition :delayer] [rd _]
  (.delayer rd))

(defmethod handle-spec [RouteDefinition :no-auto-startup] [rd _]
  (.noAutoStartup rd))

(defmethod handle-spec [RouteDefinition :auto-startup] [rd _]
  (.autoStartup rd))

(defmethod handle-spec [RouteDefinition :startup-order] [rd _ order]
  (.startupOrder rd order))

(defmethod handle-spec [RouteDefinition :route-policy] [rd _ policies]
  (.routePolicy rd policies))

(defmethod handle-spec [RouteDefinition :route-policy-ref] [rd _ policy-ref]
  (.routePolicyRef rd policy-ref))

(defmethod handle-spec [RouteDefinition :shutdown-route] [rd _ default-or-defer]
  (.shutdownRoute rd default-or-defer))

(defmethod handle-spec [RouteDefinition :shutdown-running-task] [rd _ complete-current-or-all]
  (.shutdownRunningTask rd complete-current-or-all))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         INTERCEPT DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [RouteBuilder :intercept] [rb _]
  (.intercept rb))

(defmethod handle-spec [InterceptDefinition :when] [id _ pred]
  (.when id pred))

(defmethod handle-spec [RouteBuilder :intercept-from]
  ([rb _] (.interceptFrom rb))
  ([rb _ uri] (.interceptFrom rb uri)))

(defmethod handle-spec [RouteBuilder :intercept-send-to-endpoint] [rb _ uri]
  (.interceptSendToEndpoint rb uri))

(defmethod handle-spec [InterceptSendToEndpointDefinition :when] [id _ pred]
  (.when id pred))

(defmethod handle-spec [InterceptSendToEndpointDefinition :skip-send-to-original-endpoint] [id _]
  (.setSkipSendToOriginalEndpoint id))

(defmethod handle-spec [RouteBuilder :on-exception] [rb _ exceptions]
  (.onException rb exceptions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         ONCOMPLETION DEFINITION
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod handle-spec [RouteBuilder :on-completion] [rb _]
  (.onCompletion rb))

(derive OnCompletionDefinition ::executor-service-aware)

(defmethod handle-spec [OnCompletionDefinition :on-complete-only] [ocd _]
  (.onCompleteOnly ocd))

(defmethod handle-spec [OnCompletionDefinition :on-failure-only] [ocd _]
  (.onFailureOnly ocd))

(defmethod handle-spec [OnCompletionDefinition :on-when] [ocd _ pred]
  (.onWhen ocd pred))

(defmethod handle-spec [OnCompletionDefinition :use-original-body] [ocd _]
  (.useOriginalBody ocd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                     ERROR HANDLER BUILDER
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod handle-spec [RouteBuilder :error-handler] [rb _ error-handler-builder]
  (.errorHandler rb error-handler-builder))

(defn error-handler [rb error-handler-spec]
  (.errorHandler (handle-spec rb error-handler-spec)))  

(defmethod handle-spec [DefaultErrorHandlerBuilder :redelivery-delay] [ed _ delay]
  (.redeliveryDelay ed delay))

(defmethod handle-spec [DefaultErrorHandlerBuilder :maximum-redeliveries] [ed _ max]
  (.maximumRedeliveries ed max))

(defmethod handle-spec [DefaultErrorHandlerBuilder :maximum-redelivery-delay] [ed _ max]
  (.maximumRedeliveryDelay ed max))

(defmethod handle-spec [DefaultErrorHandlerBuilder :use-exponential-back-off] [ed _]
  (.useExponentialBackOff ed))

(defmethod handle-spec [DefaultErrorHandlerBuilder :back-off-multiplier] [ed _ m]
  (.backOffMultiplier ed m))

(defmethod handle-spec [DefaultErrorHandlerBuilder :use-collision-avoidance] [ed _]
  (.useCollisionAvoidance ed))

(defmethod handle-spec [DefaultErrorHandlerBuilder :retries-exhausted-log-level] [ed _ level]
  (.retriesExhaustedLogLevel ed level))

(defmethod handle-spec [DefaultErrorHandlerBuilder :retry-attempted-log-level] [ed _ level]
  (.retryAttemptedLogLevel ed level))


(defmethod handle-spec [DefaultErrorHandlerBuilder :log-stack-trace] [ed _ y-or-n]
  (.logStackTrace ed y-or-n))

(defmethod handle-spec [DefaultErrorHandlerBuilder :log-retry-stack-trace] [ed _ y-or-n]
  (.logRetryStackTrace ed y-or-n))

(defmethod handle-spec [DefaultErrorHandlerBuilder :log-handled] [ed _ y-or-n]
  (.logHandled ed y-or-n))

(defmethod handle-spec [DefaultErrorHandlerBuilder :log-exhausted] [ed _ y-or-n]
  (.logExhausted ed y-or-n))

(defmethod handle-spec [DefaultErrorHandlerBuilder :async-delayed-redelivery] [ed _]
  (.asyncDelayedRedelivery ed))

(defmethod handle-spec [DefaultErrorHandlerBuilder :use-original-message] [ed _]
  (.useOriginalMessage ed))

(defmethod handle-spec [DefaultErrorHandlerBuilder :delay-pattern] [ed _ pat] 
  (.delayPattern ed pat))

(defmethod handle-spec [DefaultErrorHandlerBuilder :on-redelivery] [ed _ processor] 
  (.onRedelivery ed processor))

(defmethod handle-spec [DefaultErrorHandlerBuilder :disable-redelivery] [ed _] 
  (.disableRedelivery ed))

(defmethod handle-spec [DefaultErrorHandlerBuilder :executor-service-ref] [ed _ ref] 
  (.executorServiceRef ed ref))

(defmethod handle-spec [DefaultErrorHandlerBuilder :logger] [ed _ l] 
  (.logger ed l))

(defmethod handle-spec [DefaultErrorHandlerBuilder :logging-level] [ed _ l] 
  (.loggingLevel ed l))

(defmethod handle-spec [DefaultErrorHandlerBuilder :log] [ed _ log] 
  (.log ed log))

(defmethod handle-spec [DefaultErrorHandlerBuilder :retry-while] [ed _ expression] 
  (.retryWhile ed expression))

(defmethod handle-spec [DefaultErrorHandlerBuilder :use-original-message] [ed _] 
  (.useOriginalMessage ed))

(defmethod handle-spec [LoggingErrorHandlerBuilder :level] [ed _ l] 
  (.level ed l))

(defmethod handle-spec [LoggingErrorHandlerBuilder :log] [ed _ l] 
  (.log ed l))

(defmethod handle-spec [LoggingErrorHandlerBuilder :log-name] [ed _ name] 
  (.logName ed name))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn apply-dsl-part [bldr dsl-part]
  (try
    (if (sequential? dsl-part)
      (apply handle-spec bldr dsl-part)
      (handle-spec bldr dsl-part))
    (catch Exception e
      (do
        (l/error "Error while applying DSL part: " dsl-part)
        (throw e)))))
  
(defn apply-route-dsl [root-obj dsl-list]
  (l/info "Parsing route: DSL for route --> " (first dsl-list) " ... ")
  (let [ret (loop [[first & rest] (remove nil? dsl-list)
                  res root-obj]
             (if first
               (recur rest (apply-dsl-part res first))
               res))]
    ret))

(defn add-route [rb [[key & rest-spec]  & rest :as route]]
  (let [ctx (.getContext rb)]
    (condp get  key
      #{:error-handler} (apply handle-spec rb [:error-handler (apply-route-dsl (first rest-spec) rest)])
      #{:on-exception :from :intercept :intercept-from :intercept-send-to-endpoint} (apply-route-dsl rb route)
      nil)))

(defn add-routes [ctx route-specs]
  (let [rb (RouteBuilderImpl. 
             (fn [this]
               (doseq [spec route-specs]
                 (when (seq spec)
                   (add-route this spec)))
               (l/info "Route configure done")))]
    (.addRoutes ctx rb)))
  
