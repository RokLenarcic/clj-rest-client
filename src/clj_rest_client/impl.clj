(ns clj-rest-client.impl
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :refer [starts-with?]]
            [clojure.spec.alpha :as s]
            [clojure.set :as set])
  (:import (java.net URL)))

(defn merge-maps [& values] (if (every? map? values) (apply merge-with merge-maps values) (last values)))

(defn ->param-map
  "Transform a sequence of symbols into a map of symbol names to symbols, names transformed by given fn var. E.g. {\"a\" a}."
  [syms param-xf]
  (zipmap (map (comp str param-xf) syms) syms))

(defn parse-uri
  "Parse URI into alternating fixed strings and vars."
  [uri]
  (when uri
    (->> (re-seq #"([^{]+)?(?:\{\s*([\w-]+)\s*\})?" uri)
      (mapcat (fn [[_ txt var-name]] [txt (when var-name (symbol (.trim ^String var-name)))]))
      (filter some?))))

(defn ptype
  "Extract parameter type based on metadata tags"
  [name sym]
  (let [tags #{:+ :body :form :form-map}
        param-tags (filter tags (keys (meta sym)))]
    (if (> (count param-tags) 1)
      (throw (ex-info (str "Endpoint " name " param " sym " has more than one param type tag: " param-tags) {}))
      (first param-tags))))

(defn emit-body-param
  "Emit body param map or nil, based on by-type param map"
  [params-typed jsonify-bodies]
  (when-let [bparam (first (:body params-typed))]
    (condp = jsonify-bodies
      :always `{:body (when (some? ~bparam) (json/generate-string ~bparam)) :content-type :json}
      :smart `{:body         (if (or (bytes? ~bparam) (string? ~bparam)) ~bparam (when (some? ~bparam) (json/generate-string ~bparam)))
               :content-type (if (bytes? ~bparam) :bytes :json)}
      :never `{:body ~bparam})))

(defn emit-form-params
  "Emit map of form params based on by-type param map"
  [params-typed param-xf]
  (let [form-params (apply merge {} (->param-map (:form params-typed) param-xf) (:form-map params-typed))]
    (when (not-empty form-params) {:form-params form-params})))

(defn create-vararg-spec-names [fn-name {:keys [vararg-symbols]}] (when vararg-symbols (map #(keyword (str *ns* "-" fn-name) (str %)) vararg-symbols)))

(defn emit-vararg-fn-spec
  [fn-spec {:keys [norm-symbols norm-specs]} vararg-spec-names]
  (let [keys-spec (when vararg-spec-names (list :varargs (list `s/keys* :opt-un (vec vararg-spec-names))))
        param-spec (mapcat (fn [param spec] [(keyword (str param)) spec]) norm-symbols norm-specs)
        cat-spec (apply list `s/cat (concat param-spec keys-spec))]
    (if fn-spec (list `s/& cat-spec fn-spec) cat-spec)))

(defn emit-fn-spec
  [fn-spec {:keys [symbols norm-specs vararg-specs]}]
  (let [specs (concat norm-specs vararg-specs)
        cat-spec (apply list `s/cat (mapcat (fn [param spec] [(keyword (str param)) spec]) symbols specs))]
    (if fn-spec (list `s/& cat-spec fn-spec) cat-spec)))

(defn emit-defn-params [{:keys [norm-symbols vararg-symbols]}]
  (if (not-empty vararg-symbols)
    (-> norm-symbols (conj '&) (conj {:keys (vec vararg-symbols)}))
    norm-symbols))

(defn param-map [params-n-specs]
  (let [[norm-parspec vararg-parspec] (split-with #(not= '& %) (map second params-n-specs))
        vararg-parspec (next vararg-parspec)]
    {:symbols (mapv :param (concat norm-parspec vararg-parspec))
     :norm-symbols   (mapv :param norm-parspec)
     :vararg-symbols (mapv :param vararg-parspec)
     :norm-specs    (mapv :spec norm-parspec)
     :vararg-specs  (mapv #(list `s/nilable (:spec %)) vararg-parspec)}))

(defn req-spec [fn-name uri method params-n-specs fn-spec extra {:keys [jsonify-bodies json-resp xf val-xf client defaults]}]
  (let [params (param-map params-n-specs)
        vararg-spec-names (create-vararg-spec-names fn-name params)
        params-typed (group-by (partial ptype fn-name) (:symbols params))
        query-params (vec (set/difference (into #{} (get params-typed nil)) (into #{} (parse-uri uri))))
        conformed-sym (gensym "__auto__conf")]
    `[~@(map #(list `s/def %1 %2) vararg-spec-names (:vararg-specs params))
      (defn ~fn-name ~(emit-defn-params params)
        (let [arg-spec# ~(emit-fn-spec fn-spec params)
              arg-list# ~(:symbols params)
              ~conformed-sym (s/conform arg-spec# arg-list#)      ; conform args
              x# (when (= ::s/invalid ~conformed-sym)
                   (let [ed# (s/explain-data arg-spec# arg-list#)]
                     (throw (ex-info (str "Call to " ~*ns* "/" ~(str fn-name) " did not conform to spec:\n" (with-out-str (s/explain-out ed#))) ed#))))
              ~@(mapcat #(list % (list val-xf (list 'quote %) (list (keyword (str %)) conformed-sym))) (:symbols params))]
          (~client
            (merge-maps
              ~defaults
              {:clj-rest-client.core/args arg-list#
               :clj-rest-client.core/name (symbol ~(str *ns*) ~(str fn-name))
               :query-params              (into {} (filter (comp some? second)) ~(->param-map query-params xf))
               :request-method            ~method
               :url                       (str ~@(parse-uri uri))}
              ~(merge {}
                 (emit-form-params params-typed xf)
                 (when json-resp {:as :json})
                 (emit-body-param params-typed jsonify-bodies))
              (or ~extra {})))))
      (s/fdef ~fn-name :args ~(emit-vararg-fn-spec fn-spec params vararg-spec-names))]))

(defn append-uri
  [total-path path-part root?]
  "Append path part's URI to total path so far. Path can be simple or complex."
  (let [[type val] path-part] (str (if root? "" (str total-path "/")) (if (= type :simple-path) val (:path val)))))

(defn args-vec
  [[type val]]
  "Extract arguments vector from path part."
  (if (= type :simple-path) [] (map #(vector :arg {:param %1 :spec %2}) (distinct (filter symbol? (parse-uri (:path val)))) (:args val))))

(defn norm-method
  [method]
  "Convert different representation of HTTP method to clj-http standard, which is lowercased keyword."
  (if (symbol? method) (keyword (.toLowerCase (str method))) method))

(defn extract-defs [structure total-path total-args opts root?]
  "Traverse structure and emit a sequence of defn forms"
  (mapcat
    (fn [{:keys [method def path-part more]}]
      (if path-part
        (if (map? more)
          (extract-defs more (append-uri total-path path-part root?) (into total-args (args-vec path-part)) opts false)
          (throw (ex-info (str "Path " (append-uri total-path path-part root?) " must point to map not " more) {})))
        (let [{:keys [name fn-spec args extra]} def]
          (req-spec name total-path (norm-method method) (into total-args args) fn-spec extra opts))))
    (vals structure)))

(defn load-from-url [name]
  ; Add -Djava.protocol.handler.pkgs=org.my.protocols to enable custom protocols
  (when name
    (edn/read-string (slurp (if (starts-with? name " classpath: ") (io/resource (subs name 10)) (URL. name))))))
