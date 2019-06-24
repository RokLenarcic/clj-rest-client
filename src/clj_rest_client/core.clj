(ns clj-rest-client.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :refer [ends-with?]]
    [clj-rest-client.edn :as edn]
    [clj-rest-client.parse :as parse]
    [clj-rest-client.impl :refer :all]))

(s/def ::jsonify-bodies any?)
(s/def ::json-responses any?)
(s/def ::json-opts any?)
(s/def ::defaults any?)
(s/def ::param-transform any?)
(s/def ::post-process-fn any?)
(s/def ::val-transform any?)
(s/def ::path-prefix string?)
(s/def ::fdef? boolean?)
(s/def ::edn-readers map?)
(s/def ::edn-key any?)
(s/def ::default-method ::parse/method)

(s/def ::options (s/keys* :opt-un [::edn-key ::edn-readers ::jsonify-bodies ::post-process-fn ::json-responses ::param-transform ::val-transform ::defaults ::json-opts ::default-method ::fdef?]))

(defn default-val-transform
  "Default transformation that is used on params' values"
  [_ v] (if (keyword? v) (name v) v))

(defn prefix-middleware
  "Function for creating a clj-http middleware that prepends to url."
  [url-prefix]
  (let [url-prefix (if (ends-with? url-prefix "/") url-prefix (str url-prefix "/"))]
    (fn [client]
      (fn
        ([req] (client (update req :url (partial str url-prefix))))
        ([req respond raise] (client (update req :url (partial str url-prefix)) respond raise))))))

(defmacro defrest* [definition & {:keys [json-responses fdef? jsonify-bodies param-transform val-transform post-process-fn defaults json-opts default-method]
                                  :or {json-responses true jsonify-bodies :smart post-process-fn identity defaults {} default-method :get}}]
  (let [opts-map {:fdef?           fdef?
                  :jsonify-bodies  (eval jsonify-bodies)
                  :json-resp?      (eval json-responses)
                  :defaults        (gensym "__auto__defaults")
                  :xf              (or (eval param-transform) identity)
                  :val-xf          (gensym "__auto__valxf")
                  :post-process-fn (gensym "__auto__ppfn")
                  :json-opts       (gensym "__auto__json_opts")}
        defs (mapcat #(emit-declarations % opts-map)
               (parse/parse-defs definition default-method))]
    `(let [~(:post-process-fn opts-map) ~post-process-fn
           ~(:val-xf opts-map) ~(or val-transform `default-val-transform)
           ~(:defaults opts-map) ~defaults
           ~(:json-opts opts-map) ~json-opts]
       ~@defs (quote ~(map second (filter #(= `defn (first %)) defs))))))

(s/fdef defrest* :args (s/cat :def ::parse/terms :opts ::options))

(defmacro defrest
  "Defines multiple functions based on definition map. For map's structure see docs.

  Valid definition parameters any of the following: map literal, a symbol (naming a var that resolves to a map), a string URL.
  URL string can also use `classpath:` or `file:` protocol.

  Definition can be followed by opts key-value arguments, all of them are optional.

  `:post-process-fn` This option specifies a function that is invoked after generating clj-http in API function. Defaults to identity.

  `:default-method` sets the default method for endpoints with no method specified, defaults to `:get`\n

  `:param-transform` This option specifies function that is used to transform query/form/key param kw/symbol to final name. Default `identity`.

  `:val-transform` This option specifies a function that is applied to all arguments after argument spec and conform and before being embedded into
   request map. It's a function of two arguments: param name and param value, returns new param value.
   Defaults to a function that converts keyword values to a string.

  `:jsonify-bodies` set to `:always`, `:smart`, `:never`. Body params will be ran through serializer if set to `:always`. Option `:smart` will not
  run string or bytes bodies through JSON serializer. Defaults to :smart.

  `:json-responses` If true then all requests specify `{:as :json}` and all responses are expected to be json responses. Default true.

  `:json-opts` map of opts passed to Cheshire generate-string when used by this library

  `:defaults` map of default clj-http params added to every call, defaults to {}

  `:edn-readers` map of reader to supply to edn reading process when invoking with a string argument

  `:edn-key` when loading EDN use a specific key in loaded EDN, useful when using refs

  `:fdef?` emit a fdef for generated functions, defaults to false
  "
  [definition & {:keys [edn-readers edn-key] :as args}]
  `(defrest*
     ~(cond
       (symbol? definition) (var-get (resolve &env definition))
       (string? definition) (edn/load-from-url definition edn-readers edn-key)
       :default definition) ~@(mapcat identity (or args {}))))

(s/fdef defrest :args (s/cat :def (s/or :map map? :path string? :var symbol?) :opts ::options))
