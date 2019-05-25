(ns clj-rest-client.edn
  (:require [clojure.java.io :as io]
            [clojure.string :refer [starts-with?]]
            [clojure.edn :as edn]
            [clojure.walk :as walk])
  (:import (java.net URL)))

(defrecord Ref [key])

(defn ref-obj [key]
  (->Ref key))

(defn kw-ns [kw]
  (let [aliases (assoc (ns-aliases *ns*) (symbol "") *ns*)
        str-ns (str (namespace kw))
        real-ns (str (get aliases (symbol str-ns) str-ns))]
    (keyword real-ns (name kw))))

(def default-readers
  {'crc/kw kw-ns
   'crc/ref ref-obj})

(defn resolve-refs [ds]
  (walk/postwalk #(if (instance? Ref %) ((:key %) ds) %) ds))

(defn load-from-url [name readers edn-key]
  ; Add -Djava.protocol.handler.pkgs=org.my.protocols to enable custom protocols
  (when name
    (let [edn
          (edn/read-string {:readers (merge default-readers readers)}
            (slurp (if (starts-with? name "classpath:") (io/resource (subs name 10)) (URL. name))))
          resolved (resolve-refs edn)]
      (if edn-key (edn-key resolved) resolved))))
