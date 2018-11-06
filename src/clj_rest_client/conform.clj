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

(defmacro ->date-format
  "Conformer for java.util.Date and java.time objects using the supplied formatter.

  You can supply time object constructor fn for unform. This fn is called with java.time.TemporalAccessor parse object.
  Defaults to `java.time.Instant/from`"
  ([formatter]
    `(->date-format ~formatter Instant/from))
  ([formatter parse-type-constructor]
   `(letfn [(create-spec# [param-gfn#]
              (reify
                s/Spec
                (conform* [spec# x#]
                  (cond
                    (nil? x#) nil
                    (inst? x#) (.format ~formatter (OffsetDateTime/ofInstant (Instant/ofEpochMilli (.getTime ^Date x#)) ZoneOffset/UTC))
                    (instance? TemporalAccessor x#) (.format ~formatter x#)
                    :default ::s/invalid))
                (unform* [spec# y#]
                  (cond
                    (nil? y#) y#
                    (string? y#) (~parse-type-constructor (.parse ^DateTimeFormatter  ~formatter y#))
                    :default ::s/invalid))
                (explain* [spec# path# via# in# x#]
                  (when (= (s/conform* spec# x#) ::s/invalid)
                    [{:path path# :pred (list '->date-format '~formatter) :val x# :via via# :in in#}]))
                (gen* [spec# overrides# path# rmap#]
                  (if param-gfn#
                    (param-gfn#)
                    (s/gen* (s/spec inst?) overrides# path# rmap#)))
                (with-gen* [spec# gfn#]
                  (create-spec# gfn#))
                (describe* [spec#] (list '->date-format '~formatter))))]
      (create-spec# nil))))
