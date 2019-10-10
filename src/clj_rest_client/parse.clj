(ns clj-rest-client.parse
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]))

(s/def ::kw-or-sym #(or (symbol? %) (keyword? %)))

(s/def ::vararg-mark (s/and ::kw-or-sym #(= (name %) "&")))
(s/def ::param-type-name-pair (s/cat :type ::kw-or-sym :name ::kw-or-sym))
(s/def ::param (s/or :pair ::param-type-name-pair :solo ::kw-or-sym))
(s/def ::arg (s/cat :param ::param :schema any?))

(s/def ::args-vect
  (s/& vector? (s/* (s/alt :vararg ::vararg-mark :arg ::arg))))

(s/def ::endpointdef
  (s/spec (s/cat :fn-name ::kw-or-sym
            :fn-schema (s/? any?)
            :args ::args-vect
            :extra (s/? (complement vector?)))))
(s/def ::simple-path (s/and string? #(not= (first %) \/)))
(s/def ::path (s/or :simple-path ::simple-path :complex-path (s/cat :path ::simple-path :args (s/+ any?))))
(s/def ::method ::kw-or-sym)
(s/def ::term (s/or
                :endpoint
                (s/cat :method ::method :def ::endpointdef)
                :subpath
                (s/cat :path-part ::path :more (s/or :terms ::terms :get-endpoint ::endpointdef))))
(s/def ::terms (s/coll-of ::term :kind map?))

(defn parse-uri
  "Parse URI into alternating fixed strings and vars."
  [uri]
  (when uri
    (->> (re-seq #"([^{]+)?(?:\{\s*([\w-]+)\s*\})?" uri)
      (mapcat (fn [[_ txt var-name]] [txt (when var-name (keyword (.trim ^String var-name)))]))
      (filter some?))))

(defn ptype
  "Extract parameter type based on metadata tags"
  [sym]
  (let [tags #{:+ :body :form :form-map :key}
        param-tags (filter tags (keys (meta sym)))]
    (if (> (count param-tags) 1)
      (throw (ex-info (str "Param " sym " has more than one param type tag: " param-tags) {}))
      (first param-tags))))

(defn normalize-arg
  "Convert arg schema into {:param {:type \"x\" :name \"y\"} :schema ::schema} or '&"
  [arg]
  (if (= :vararg (first arg))
    '&
    (update (second arg) :param
      (fn [[param-type data]]
        (if (= :solo param-type)
          {:type (when (symbol? data) (ptype data))
           :name (name data)}
          {:type (keyword (name (:type data)))
           :name (name (:name data))})))))

(defn param-map [params-n-schemas path-params]
  (let [->param (comp :name :param)
        [norm-parschema [_ & vararg-parschema]] (split-with #(not= '& %) (map normalize-arg params-n-schemas))
        by-type (reduce #(update %1 (:type %2) (fnil conj []) (:name %2))
                  {} (map :param (concat norm-parschema vararg-parschema)))
        path-params (mapv name path-params)
        query-params (vec (set/difference (into #{} (by-type nil)) (into #{} path-params)))]
    {:names (mapv ->param (concat norm-parschema vararg-parschema))
     :norm-names (mapv ->param norm-parschema)
     :by-type (dissoc
                (merge by-type
                  {:query query-params
                   :path path-params})
                nil)
     :vararg-names (mapv ->param vararg-parschema)
     :norm-schemas (mapv :schema norm-parschema)
     :vararg-schemas (mapv :schema vararg-parschema)}))


(defn endpoint-meta [fn-name uri method params-n-schemas fn-schema extra]
  (let [parsed-uri (parse-uri uri)
        params (param-map params-n-schemas (filter keyword? parsed-uri))]
    {::fn-name (name fn-name)
     ::method (keyword (.toLowerCase (name method)))
     ::params params
     ::fn-schema fn-schema
     ::uri parsed-uri
     ::extra extra}))

(defn append-uri
  "Append path part's URI to total path so far. Path can be simple or complex."
  [total-path path-part root?]
  (let [[type val] path-part] (str (if root? "" (str total-path "/")) (if (= type :simple-path) val (:path val)))))

(defn args-vec
  "Extract arguments vector from path part."
  [[type val]]
  (if (= type :simple-path) [] (map #(vector :arg {:param [:solo %1] :schema %2}) (distinct (filter keyword? (parse-uri (:path val)))) (:args val))))

(defn parse-defs
  "Traverse structure and emit a sequence of endpoint meta objects"
  ([definition default-method]
   (let [structure (s/conform ::terms definition)]
     (when (= ::s/invalid structure)
       (throw (ex-info "" (s/explain ::terms definition))))
     (parse-defs structure "" [] default-method true)))
  ([structure total-path total-args default-method root?]
   (mapcat
     (fn [{:keys [method def path-part more]}]
       (if path-part
         (let [[mtype mval] more]
           (parse-defs
             (if (= :terms mtype)
               mval
               {default-method {:method default-method :def mval}})
             (append-uri total-path path-part root?)
             (into total-args (args-vec path-part))
             default-method
             false))
         (let [{:keys [fn-name fn-schema args extra]} def]
           [(endpoint-meta (name fn-name) total-path method (into total-args args) fn-schema extra)])))
     (vals structure))))
