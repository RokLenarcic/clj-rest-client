(ns clj-rest-client.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.set :as set]
    [clojure.string :refer [starts-with? ends-with?]]
    [clj-rest-client.spec :as spec]
    [clojure.java.io :as io]
    [clojure.edn :as edn])
  (:import (java.net URL)))

(defn merge-maps [& values] (if (every? map? values) (apply merge-with merge-maps values) (last values)))

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

(defn parse-vars
  "Parse URI into alternating fixed strings and vars."
  [uri]
  (when uri
    (->> (re-seq #"([^{]+)?(?:\{s*([\w-]+)\s*\})?" uri)
      (mapcat (fn [[_ txt var-name]] [txt (when var-name (symbol var-name))]))
      (filter some?))))

(defn- req-spec [name uri method params-n-specs extra {:keys [json-body json-resp xf val-xf]}]
  (let [params (mapv :param params-n-specs)
        body-param (->> params (filter (comp :body meta)) first)
        hidden-params (->> params (filter (comp :+ meta)))
        query-params (set/difference (into #{} params) (into #{} (concat [body-param] hidden-params (parse-vars uri))))
        conformed-sym (gensym "__auto__conf")]
    `[(defn ~name ~params
        (let [arg-spec# (s/cat ~@(mapcat (fn [{:keys [param spec]}] [(keyword (str param)) spec]) params-n-specs))
              ~conformed-sym (s/conform arg-spec# ~params)      ; conform args
              x# (when (= ::s/invalid ~conformed-sym)
                    (let [ed# (s/explain-data arg-spec# ~params)]
                      (throw (ex-info (str "Call to " ~*ns* "/" ~(str name) " did not conform to spec:\n" (with-out-str (s/explain-out ed#))) ed#))))
              ~@(mapcat #(list % (list val-xf (list 'quote %) (list (keyword (str %)) conformed-sym))) params)]
          (merge-maps
            {:query-params   (into {} (filter (comp some? second))
                               ~(zipmap (map (comp str (var-get (resolve xf))) query-params) query-params))
             :request-method ~method
             :url            (str ~@(parse-vars uri))}
            ~(merge
               {}
               (when json-resp {:as :json})
               (when body-param (if json-body {:form-params body-param :content-type :json} {:body body-param})))
            (or ~extra {}))))
      (s/fdef ~name :args (s/cat ~@(mapcat (fn [{:keys [param spec]}] [(keyword (str param)) spec]) params-n-specs)))]))

(defn- extract-defs [structure path prepend-args opts root?]
  "Traverse structure and emit a sequence of defn forms"
  (let [str-path-part (fn [[type val]] (str (if root? "" (str path "/")) (if (= type :simple-path) val (:path val))))
        path-args
        (fn [[type val]]
          (if (= type :simple-path) [] (map #(hash-map :param %1 :spec %2) (distinct (filter symbol? (parse-vars (:path val)))) (:args val))))]
    (mapcat
      (fn [{:keys [method def path-part more]}]
        (if path-part
          (if (map? more)
            (extract-defs more (str-path-part path-part) (into prepend-args (path-args path-part)) opts false)
            (throw (ex-info (str "Path " (str-path-part path-part) " must point to map not " more) {})))
          (let [keyword-method (if (symbol? method) (keyword (.toLowerCase (str method))) method)
                {:keys [name args extra]} def]
            (req-spec name path keyword-method (into prepend-args args) extra opts))))
      (vals structure))))

(defn- load-from-url [name]
  ; Add -Djava.protocol.handler.pkgs=org.my.protocols to enable custom protocols
    (when name
      (edn/read-string (slurp (if (starts-with? name " classpath: ") (io/resource (subs name 10)) (URL. name))))))

(defmacro defrest-map [definition {:keys [json-responses json-bodies param-transform val-transform]
                                   :or {json-responses true json-bodies true}}]
  (let [defs (extract-defs (s/conform ::spec/terms definition) "" [] {:json-body json-bodies :json-resp json-responses :xf (or param-transform 'identity) :val-xf (or val-transform `default-val-transform)} true)]
    `(do ~@defs (quote ~(map second (filter #(= `defn (first %)) defs))))))

(s/fdef defrest-map :args (s/cat :def ::spec/terms :opts ::spec/options))

(defmacro defrest
  "Defines multiple functions based on definition map. For map's structure see docs.

  Valid definition parameters any of the following: map literal, a symbol (naming a var that resolves to a map), a string URL.
  URL string can use `classpath:` or `file:` protocol.

  Definition can be followed by opts key-value arguments, all of them are optional.

  `:param-transform` This option specifies function that is uset to transform query parameter names: parameter name (symbol) -> query parameter name (string). Default `identity`.
  `:val-transform` This option specifies a function that is applied to all arguments after argument spec and conform and before being embedded into
   request map. It's a function of two arguments: param name symbol and param value, returns new param value.
   Defaults to a function that converts keywords to a string name (no ns).
  `:json-bodies` If true then body parameters are sent as to-JSON serialized form params, otherwise body params are simply added to request as `:body`. Default true.
  `:json-responses` If true then all requests specify `{:as :json}` and all responses are expected to be json responses. Default true.
  "
  [definition & {:keys [] :as args}]
  `(defrest-map
     ~(cond
       (symbol? definition) (var-get (resolve &env definition))
       (string? definition) (load-from-url definition)
       :default definition) ~(or args {})))

(s/fdef defrest :args (s/cat :def (s/or :map map? :classpath string? :var symbol?) :opts ::spec/options*))
