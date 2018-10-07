(ns clj-rest-client.spec
  (:require [clojure.spec.alpha :as s]))

(s/def ::endpointdef
  (s/spec (s/cat :name symbol?
                 :args (s/? (s/& vector? (s/* (s/cat :param symbol? :spec any?))))
                 :extra (s/? map?))))
(s/def ::path (s/and string? #(not= (first %) \/)))
(s/def ::method #(or (symbol? %) (keyword? %)))
(s/def ::term (s/or :subpath (s/cat :path ::path :more ::terms) :endpoint (s/cat :method ::method :def ::endpointdef)))
(s/def ::terms (s/coll-of ::term :kind map?))
(s/def ::json-bodies boolean?)
(s/def ::json-responses boolean?)
(s/def ::instrument boolean?)
(s/def ::param-transform ifn?)
(s/def ::val-transform ifn?)
(s/def ::path-prefix string?)

(s/def ::options* (s/keys* :opt-un [::json-bodies ::json-responses ::param-transform ::instrument ::val-transform]))
(s/def ::options (s/keys :opt-un [::json-bodies ::json-responses ::param-transform ::instrument ::val-transform]))
