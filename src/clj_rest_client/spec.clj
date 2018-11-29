(ns clj-rest-client.spec
  (:require [clojure.spec.alpha :as s]))

(s/def ::endpointdef
  (s/spec (s/cat :name symbol?
                 :fn-spec (s/? any?)
                 :args (s/& vector? (s/* (s/alt :vararg #(= % '&) :arg (s/cat :param symbol? :spec any?))))
                 :extra (s/? (complement vector?)))))
(s/def ::simple-path (s/and string? #(not= (first %) \/)))
(s/def ::path (s/or :simple-path ::simple-path :complex-path (s/cat :path ::simple-path :args (s/+ any?))))
(s/def ::method #(or (symbol? %) (keyword? %)))
(s/def ::term (s/or :subpath (s/cat :path-part ::path :more ::terms) :endpoint (s/cat :method ::method :def ::endpointdef)))
(s/def ::terms (s/coll-of ::term :kind map?))
(s/def ::jsonify-bodies #{:always :smart :never})
(s/def ::json-responses boolean?)
(s/def ::instrument boolean?)
(s/def ::varargs boolean?)
(s/def ::param-transform any?)
(s/def ::client any?)
(s/def ::val-transform any?)
(s/def ::path-prefix string?)

(s/def ::options* (s/keys* :opt-un [::jsonify-bodies ::client ::json-responses ::param-transform ::instrument ::val-transform ::varargs]))
(s/def ::options (s/keys :opt-un [::jsonify-bodies ::client ::json-responses ::param-transform ::instrument ::val-transform ::varargs]))
