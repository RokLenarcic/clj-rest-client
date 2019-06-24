(ns clj-rest-client.impl
  (:require [cheshire.core :as json]
            [meta-merge.core :refer [meta-merge]]
            [clj-rest-client.parse :as parse]
            [clojure.spec.alpha :as s])
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

(defn emit-spec-check
  "Emit let vector elements that will conform input to spec and save into symbol"
  [{::parse/keys [params fn-name fn-spec]} conformed-sym]
  (let [specs (concat (:norm-specs params) (map #(list `s/nilable %) (:vararg-specs params)))
        cat-spec (apply list `s/cat (mapcat (fn [param spec] [(keyword param) spec]) (:names params) specs))]
    `[arg-spec# ~(if fn-spec (list `s/& cat-spec fn-spec) cat-spec)
      arg-list# ~(mapv symbol (:names params))
      ~conformed-sym (s/conform arg-spec# arg-list#)      ; conform args
      x# (when (= ::s/invalid ~conformed-sym)
           (let [ed# (s/explain-data arg-spec# arg-list#)]
             (throw (ex-info (str "Call to " ~*ns* "/" ~fn-name " did not conform to spec:\n" (with-out-str (s/explain-out ed#))) ed#))))]))

(defn emit-fdef
  [{::parse/keys [params fn-name fn-spec]}]
  (let [{:keys [vararg-names vararg-specs norm-specs norm-names]} params
        vararg-spec-names (map #(keyword (str *ns* "-" fn-name) %) vararg-names)
        keys-spec (when vararg-names (list :varargs (list `s/keys* :opt-un (vec vararg-spec-names))))
        param-spec (mapcat (fn [param spec] [(keyword param) spec]) norm-names norm-specs)
        cat-spec (apply list `s/cat (concat param-spec keys-spec))]
    (concat
      (map #(list `s/def %1 %2) vararg-spec-names vararg-specs)
      [`(s/fdef ~(symbol fn-name) :args ~(if fn-spec (list `s/& cat-spec fn-spec) cat-spec))])))

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

(defn destructure-conformed [{::parse/keys [params]} conformed-sym val-xf]
  (mapcat #(list (symbol %) (list val-xf (list symbol %) (list (keyword %) conformed-sym))) (:names params)))

(defn emit-declarations
  [{::parse/keys [fn-name params] :as parsed}
   {:keys [xf fdef? post-process-fn defaults json-resp? jsonify-bodies json-opts val-xf]}]
  (let [conformed-sym (gensym "__auto__conf")]
    `[~@(when fdef? (emit-fdef parsed))
      (defn ~(symbol (name fn-name)) ~(emit-defn-params parsed)
        (let [~@(emit-spec-check parsed conformed-sym)
              ~@(destructure-conformed parsed conformed-sym val-xf)]
          (~post-process-fn
            (serialize-body
              (meta-merge
                {:clj-rest-client.core/args ~(mapv symbol (:names params))
                 :clj-rest-client.core/name (symbol ~(str *ns*) ~fn-name)}
                ~(assemble-request-base parsed xf defaults json-resp?))
              ~jsonify-bodies
              ~json-opts))))]))

