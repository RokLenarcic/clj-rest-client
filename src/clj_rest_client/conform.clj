(ns clj-rest-client.conform
  (:require [clojure.spec.alpha :as s]
            [cheshire.core :as json])
  (:import (java.time.temporal TemporalAccessor ChronoField)
           (java.time ZoneOffset Instant OffsetDateTime LocalDateTime ZonedDateTime LocalDate OffsetTime LocalTime YearMonth Year)
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

(defn parse-dt
  "Converts temporal accessor to java.time object depending on the availability of data:
  - LocalDate
  - LocalTime, OffsetTime
  - LocalDateTime, ZonedDateTime
  - YearMonth
  - Year"
  [^TemporalAccessor parsed]
  (let [[time date month zone] (map #(.isSupported parsed %) [ChronoField/NANO_OF_DAY ChronoField/EPOCH_DAY ChronoField/MONTH_OF_YEAR ChronoField/OFFSET_SECONDS])]
    (if time
      (if date
        (if zone (ZonedDateTime/from parsed) (LocalDateTime/from parsed))
        (if zone (OffsetTime/from parsed) (LocalTime/from parsed)))
      (if date
        (LocalDate/from parsed)
        (if month (YearMonth/from parsed) (Year/from parsed))))))

(defmacro ->date-format
  "Conformer for java.util.Date and java.time objects using the supplied formatter.

  You can supply time object constructor fn for unform. This fn is called with java.time.TemporalAccessor parse object.
  Defaults to `clj-rest-client.conform/parse-dt` (see its docs)."
  ([formatter]
    `(->date-format ~formatter parse-dt))
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
                    (string? y#) (~parse-type-constructor (.parse ^DateTimeFormatter ~formatter y#))
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
