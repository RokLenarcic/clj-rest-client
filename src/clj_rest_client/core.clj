(ns clj-rest-client.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as stest]
    [clojure.string :refer [starts-with? ends-with?]]
    [clj-rest-client.spec :as spec]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.edn :as edn])
  (:import (java.net URL)))

(defn merge-maps [& values] (if (every? map? values) (apply merge-with merge-maps values) (last values)))

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
  (->> (re-seq #"([^{]+)?(?:\{s*([\w-]+)\s*\})?" uri)
       (mapcat (fn [[_ txt var-name]] [txt (when var-name (symbol var-name))]))
       (filter some?)))

(defn- req-spec [name uri method params-n-specs extra {:keys [json-body json-resp xf instrument]}]
  (let [params (mapv :param params-n-specs)
        body-param (first (filter (comp :body meta) params))
        query-params (reduce disj (apply hash-set (remove (comp :+ meta) params)) (cons body-param (parse-vars uri)))]
    `[(defn ~name ~params
        (merge-maps
          {:query-params   (into {} (filter second)
                                 ~(zipmap (map (comp str (var-get (resolve xf))) query-params)
                                          (map #(if (:json (meta %)) `(json/generate-string ~%) %) query-params)))
           :request-method ~method
           :url           (str ~@(parse-vars uri))}
          ~(merge
             {}
             (when json-resp {:as :json})
             (when body-param (if json-body {:form-params body-param :content-type :json} {:body body-param})))
          (or ~extra {})))
      (~(if instrument `stest/instrument `identity)
        (s/fdef ~name :args (s/cat ~@(mapcat (fn [{:keys [param spec]}] [(keyword (str param)) spec]) params-n-specs))))]))

(defn- extract-defs [structure path opts root?]
  "Traverse structure and emit a sequence of defn forms"
  (mapcat
    (fn [[k v]]
      (if (string? k)
        (if (map? v)
          (extract-defs v (if root? k (str path "/" k)) opts false)
          (throw (ex-info (str "Path " path "/" k " must point to map not " v) {})))
        (let [keyword-method (if (symbol? k) (keyword (.toLowerCase (str k))) k)
              {:keys [name args extra]} (s/conform ::spec/endpointdef v)]
          (req-spec name path keyword-method args extra opts))))
    structure))

(defn- load-from-url [name]
  ; Add -Djava.protocol.handler.pkgs=org.my.protocols to enable custom protocols
    (when name
      (edn/read-string (slurp (if (starts-with? name " classpath: ") (io/resource (subs name 10)) (URL. name))))))

(defmacro defrest-map [definition {:keys [json-responses json-bodies param-transform instrument]
                                   :or {json-responses true json-bodies true instrument true}}]
  (let [defs (extract-defs definition "" {:json-body json-bodies :json-resp json-responses :xf (or param-transform 'identity) :instrument instrument} true)]
    `(do ~@defs (quote ~(map second (filter #(= `defn (first %)) defs))))))

(s/fdef defrest-map :args (s/cat :def ::spec/terms :opts ::spec/options))

(defmacro defrest
  "Defines multiple functions based on definition map. For map's structure see docs.

  Valid definition parameters any of the following: map literal, a symbol (naming a var that resolves to a map), a string URL.
  URL string can use `classpath:` or `file:` protocol.

  Definition can be followed by opts key-value arguments, all of them are optional.

  `:param-transform` This option specifies function that is transformation: parameter (symbol) -> query parameter name (string). Default `identity`.
  `:json-bodies` If true then body parameters are sent as to-JSON serialized form params, otherwise body params are simply added to request as `:body`. Default true.
  `:json-responses` If true then all requests specify `{:as :json}` and all responses are expected to be json responses. Default true.
  `:instrument` Every function defined by `defrest` has its own `fdef` args spec. If instrument option is true, then all generated functions are also instrumented. Default true.
  "
  [definition & {:keys [] :as args}]
  `(defrest-map
     ~(cond
       (symbol? definition) (var-get (resolve definition))
       (string? definition) (load-from-url definition)
       :default definition) ~(or args {})))

(s/fdef defrest :args (s/cat :def (s/or :map map? :classpath string? :var symbol?) :opts ::spec/options*))
