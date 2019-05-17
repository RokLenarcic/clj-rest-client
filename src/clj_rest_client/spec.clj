(ns clj-rest-client.spec
  (:require [clojure.spec.alpha :as s]))

(s/def ::symbol-non-ns
  (s/and symbol? (s/conformer #(with-meta (symbol (name %)) (meta %)))))

(s/def ::endpointdef
  (s/spec (s/cat :name ::symbol-non-ns
                 :fn-spec (s/? any?)
                 :args (s/& vector? (s/* (s/alt :vararg (s/and ::symbol-non-ns #(= % '&)) :arg (s/cat :param ::symbol-non-ns :spec any?))))
                 :extra (s/? (complement vector?)))))
(s/def ::simple-path (s/and string? #(not= (first %) \/)))
(s/def ::path (s/or :simple-path ::simple-path :complex-path (s/cat :path ::simple-path :args (s/+ any?))))
(s/def ::method #(or (symbol? %) (keyword? %)))
(s/def ::term (s/or
                :endpoint
                (s/cat :method ::method :def ::endpointdef)
                :subpath
                (s/cat :path-part ::path :more (s/or :terms ::terms :get-endpoint ::endpointdef))))
(s/def ::terms (s/coll-of ::term :kind map?))
(s/def ::jsonify-bodies any?)
(s/def ::name-transform any?)
(s/def ::json-responses any?)
(s/def ::json-opts any?)
(s/def ::defaults any?)
(s/def ::param-transform any?)
(s/def ::client any?)
(s/def ::val-transform any?)
(s/def ::path-prefix string?)
(s/def ::default-method ::method)

(s/def ::options* (s/keys* :opt-un [::jsonify-bodies ::client ::json-responses ::param-transform ::val-transform ::defaults ::name-transform ::json-opts ::default-method]))
(s/def ::options (s/keys :opt-un [::jsonify-bodies ::client ::json-responses ::param-transform ::val-transform ::defaults ::name-transform ::json-opts ::default-method]))
