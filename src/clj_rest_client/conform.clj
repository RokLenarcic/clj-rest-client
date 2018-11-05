(ns clj-rest-client.conform
  (:require [clojure.spec.alpha :as s]
            [cheshire.core :as json])
  (:import (java.time.temporal TemporalAccessor)
           (java.time ZoneOffset Instant OffsetDateTime)
           (java.util Date)
           (java.time.format DateTimeFormatter)))

(def ^{:doc "A conformer that conforms to JSON" } ->json (s/conformer json/generate-string json/parse-string))
(def ^{:doc "A conformer that conforms to JSON, but it doesn't convert nil to \"null\"" } ->json?
  (s/conformer #(some-> % json/generate-string) #(some-> % json/parse-string)))

(defn ->json*
  "Create a new conformer that conforms to JSON with added params."
  [cheshire-opts]
  (s/conformer #(json/generate-string % cheshire-opts) #(json/parse-string % cheshire-opts)))

(defn ->json?*
  "Create a new conformer that conforms to JSON with added params, but it doesn't convert nil to \"null\""
  [cheshire-opts]
  (s/conformer #(some-> % (json/generate-string cheshire-opts)) #(some-> % (json/parse-string cheshire-opts))))

(defn to-format [^DateTimeFormatter formatter]
  (fn [v]
    (cond
      (nil? v) v
      (inst? v) (.format formatter (OffsetDateTime/ofInstant (Instant/ofEpochMilli (.getTime ^Date v)) ZoneOffset/UTC))
      (instance? TemporalAccessor v) (.format formatter v)
      :default ::s/invalid)))

(defmacro ->date-format
  "Conformer for java.util.Date and java.time objects using the supplied formatter."
  [formatter]
  `(s/conformer (to-format ~formatter)))

(s/fdef ->date-format :args (s/cat :formatter #(instance? DateTimeFormatter %)))