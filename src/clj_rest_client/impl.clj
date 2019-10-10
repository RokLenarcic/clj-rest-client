(ns clj-rest-client.impl
  (:require [cheshire.core :as json]
            [meta-merge.core :refer [meta-merge]]
            [clj-rest-client.parse :as parse]
            [malli.core :as m]
            [malli.error :as me])
  (:import (clojure.lang Named)))

(defn make-fragment
  ([main-key m]
   (make-fragment
     main-key
     (map #(if (instance? Named %) (name %) (str %)) (keys m))
     (vals m)))
  ([main-key sub-keys sub-vals]
   (let [m (into {} (filter (comp some? second)) (map #(vector %1 %2) sub-keys sub-vals))]
     (when (not-empty m) {main-key m}))))

(defn emit-form-params
  "Emit form for processing of form params based on by-type param map"
  [parsed xf]
  (when-let [form-params (-> parsed ::parse/params :by-type :form)]
    (list `make-fragment :form-params (mapv xf form-params) (mapv symbol form-params))))

(defn emit-query-params
  "Emit form for processing of query params based on by-type param map"
  [parsed xf]
  (when-let [query-params (-> parsed ::parse/params :by-type :query)]
    (list `make-fragment :query-params (mapv xf query-params) (mapv symbol query-params))))

(defn emit-form-map-params
  "Emit params for form map."
  [parsed xf]
  (when-let [form-map-params (-> parsed ::parse/params :by-type :form-map)]
    (list `make-fragment :form-params (apply list `meta-merge (mapv symbol form-map-params)))))

(defn emit-body-params
  "Emit body params as body property."
  [parsed]
  (when-let [body-params (-> parsed ::parse/params :by-type :body)]
    `(when-some [body# (meta-merge ~@(mapv symbol body-params))]
       {:body body#})))

(defn emit-key-params
  "Emit key params into body property"
  [parsed xf]
  (when-let [key-params (-> parsed ::parse/params :by-type :key)]
    (list `make-fragment :body (mapv xf key-params) (mapv symbol key-params))))

(defn assemble-request-base
  "Assemble request base with the various param types"
  [parsed xf defaults json-resp?]
  (apply
    list
    `meta-merge
    defaults
    (filter some?
      [(merge {:request-method (::parse/method parsed)
               :url (apply list `str (map #(if (keyword? %) (symbol (name %)) %) (::parse/uri parsed)))}
              (when json-resp? {:as :json}))
       (emit-query-params parsed xf)
       (emit-form-map-params parsed xf)
       (emit-form-params parsed xf)
       ; body stuff
       (emit-body-params parsed)
       (emit-key-params parsed xf)
       (::parse/extra parsed)])))

(defn emit-defn-params
  "Emit defn parameter vector."
  [{::parse/keys [params]}]
  (let [{:keys [vararg-names norm-names]} params]
    (if (not-empty vararg-names)
      (-> (mapv symbol norm-names) (conj '&) (conj {:keys (mapv symbol vararg-names)}))
      (mapv symbol norm-names))))

(defn xf-param [schema ns-sym fn-name param-name transfomer schema-registry val]
  (let [ed (m/explain schema val {:registry schema-registry})]
    (when ed
      (println ed)
      (throw (ex-info (str "Parameter \"" param-name "\" in call to "
                           ns-sym "/" fn-name
                           " did not conform to schema:\n"
                           (me/humanize ed {:wrap :message}))
                      {:type :clj-rest-client/arg-schema-error
                       :explanation ed})))
    (if transfomer
      (m/encode schema val {:registry schema-registry} transfomer)
      val)))

(defn emit-schema-check
  "Emit let vector elements that will conform input to schema and save into symbol"
  [{::parse/keys [params fn-name]} transformer-sym schema-registry-sym]
  (let [schemas (concat (:norm-schemas params) (map #(vector :maybe %) (:vararg-schemas params)))]
    (mapcat #(vector (symbol %1)
                     (list `xf-param %2 *ns* fn-name %1 transformer-sym schema-registry-sym (symbol %1)))
            (:names params)
            schemas)))

(defn emit-fn-schema-check
  [fn-name fn-schema params schema-registry-sym]
  `(if-let [ed# (m/explain ~fn-schema ~(into {} (map (juxt keyword symbol)) (:names params)) {:registry ~schema-registry-sym})]
     (throw (ex-info (str "Call to " ~*ns* "/" ~fn-name " did not conform to schema:\n"
                          (me/humanize ed# {:wrap :message}))
                     {:type :clj-rest-client/arg-schema-error
                      :explanation ed#}))))

#_(defn emit-schema [{::parse/keys [fn-name params fn-schema]}]
    `(def ~(symbol (str (name fn-name) "-sch"))
       (let [param-sch (apply vector
                              :tuple
                              (concat (:norm-schemas params) (map #(vector :maybe %) (:vararg-schemas params))))]
         (if fn-schema [:and param-sch fn-schema] param-sch))))

(defn serialize-body [req jsonify-bodies json-opts]
  (if (some? (:body req))
    (condp = jsonify-bodies
      :always (-> req
                (update :body #(json/generate-string % json-opts))
                (update :content-type #(or % :json)))
      :smart (-> req
               (update :body #(if (or (bytes? %) (string? %)) % (json/generate-string % json-opts)))
               (update :content-type #(or % (if (bytes? (:body req)) :bytes :json))))
      :never req)
    req))

(defn emit-declarations
  [{::parse/keys [fn-name params fn-schema] :as parsed}
   {:keys [xf def-schema? defaults json-resp? jsonify-bodies json-opts transformer schema-registry]}]
  `[;~(when def-schema? (emit-schema parsed))
    (defn ~(symbol (name fn-name)) ~(emit-defn-params parsed)
      (let [~@(emit-schema-check parsed transformer schema-registry)]
        ~(when fn-schema
           (emit-fn-schema-check fn-name fn-schema params schema-registry))
        (serialize-body
          (meta-merge
            {:clj-rest-client.core/args ~(mapv symbol (:names params))
             :clj-rest-client.core/name (symbol ~(str *ns*) ~fn-name)}
            ~(assemble-request-base parsed xf defaults json-resp?))
          ~jsonify-bodies
          ~json-opts)))])
