(ns clj-rest-client.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :refer [ends-with?]]
    [clj-rest-client.edn :as edn]
    [clj-rest-client.parse :as parse]
    [clj-rest-client.impl :refer :all]
    [malli.core :as m]))

(s/def ::jsonify-bodies any?)
(s/def ::json-responses any?)
(s/def ::json-opts any?)
(s/def ::defaults any?)
(s/def ::param-transform any?)
(s/def ::path-prefix string?)
(s/def ::def-schema? boolean?)
(s/def ::aero-opts map?)
(s/def ::transformer any?)
(s/def ::schema-registry any?)
(s/def ::edn-key any?)
(s/def ::default-method ::parse/method)

(s/def ::options (s/keys* :opt-un [::edn-key ::aero-opts ::jsonify-bodies
                                   ::json-responses ::param-transform ::defaults
                                   ::json-opts ::default-method ::def-schema? ::transformer
                                   ::schema-registry]))

(defn prefix-middleware
  "Function for creating a clj-http middleware that prepends to url."
  [url-prefix]
  (let [url-prefix (if (ends-with? url-prefix "/") url-prefix (str url-prefix "/"))]
    (fn [client]
      (fn
        ([req] (client (update req :url (partial str url-prefix))))
        ([req respond raise] (client (update req :url (partial str url-prefix)) respond raise))))))

(defmacro defrest* [definition & {:keys [json-responses def-schema? jsonify-bodies param-transform transformer schema-registry
                                         defaults json-opts default-method]
                                  :or   {json-responses true jsonify-bodies :smart defaults {} default-method :get schema-registry `m/default-registry}}]
  (let [opts-map {:def-schema?     def-schema?
                  :jsonify-bodies  (eval jsonify-bodies)
                  :json-resp?      (eval json-responses)
                  :defaults        (gensym "__auto__defaults")
                  :xf              (or (eval param-transform) identity)
                  :transformer     (gensym "__auto__transf")
                  :schema-registry (gensym "__auto__schema_reg")
                  :json-opts       (gensym "__auto__json_opts")}
        defs (mapcat #(emit-declarations % opts-map)
               (parse/parse-defs definition default-method))]
    `(let [~(:transformer opts-map) ~transformer
           ~(:schema-registry opts-map) ~schema-registry
           ~(:defaults opts-map) ~defaults
           ~(:json-opts opts-map) ~json-opts]
       ~@defs (quote ~(map second (filter #(= `defn (first %)) defs))))))

(s/fdef defrest* :args (s/cat :def ::parse/terms :opts ::options))

(defmacro defrest
  "Defines multiple functions based on definition map. For map's structure see docs.

  Valid definition parameters any of the following: map literal, a symbol (naming a var that resolves to a map), a string URL.
  URL string can also use `classpath:` or `file:` protocol.

  Definition can be followed by opts key-value arguments, all of them are optional.

  `:default-method` sets the default method for endpoints with no method specified, defaults to `:get`

  `:param-transform` This option specifies function that is used to transform query/form/key param kw/symbol to final name. Default `identity`.

  `:transformer` a Malli transformer that is used on parameters before they are sent

  `:schema-registry` a Malli schema-registry that is used on all Malli actions

  `:jsonify-bodies` set to `:always`, `:smart`, `:never`. Body params will be ran through serializer if set to `:always`. Option `:smart` will not
  run string or bytes bodies through JSON serializer. Defaults to :smart.

  `:json-responses` If true then all requests specify `{:as :json}` and all responses are expected to be json responses. Default true.

  `:json-opts` map of opts passed to Cheshire generate-string when used by this library

  `:defaults` map of default clj-http params added to every call, defaults to {}

  `:aero-opts` map of Aero options to be used when reading from EDN file.

  `:edn-key` when loading EDN use a specific key in loaded EDN, useful when using refs

  `:def-schema?` emit a var that contains schema for the generated function, defaults to false, var emitted
  has same name as function with `-sch` suffix
  "
  [definition & {:keys [aero-opts edn-key] :as args}]
  `(defrest*
     ~(cond
       (symbol? definition) (var-get (resolve &env definition))
       (string? definition) (edn/load-from-url definition aero-opts edn-key)
       :default definition) ~@(mapcat identity (or args {}))))

(s/fdef defrest :args (s/cat :def (s/or :map map? :path string? :var symbol?) :opts ::options))
