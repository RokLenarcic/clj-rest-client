(ns clj-rest-client.edn
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :refer [starts-with?]])
  (:import (java.net URL)))

(defmethod aero/reader 'crc/ns
  [_ _ value]
  (let [aliases (assoc (ns-aliases *ns*) (symbol "") *ns*)
        str-ns (str (namespace value))
        real-ns (str (get aliases (symbol str-ns) str-ns))]
    (keyword real-ns (name value))))

(defn load-from-url
  "Load EDN denoted by the name parameter using Aero. It will load

  Uses the map of aero opts, and then
  retrieves the key edn-key or returns whole if edn-key is not defined.

  Add -Djava.protocol.handler.pkgs=org.my.protocols to enable custom protocols"
  [name aero-opts edn-key]
  (when name
    (let [edn (aero/read-config (if (starts-with? name "classpath:")
                                  (io/resource (subs name 10))
                                  (URL. name))
                                (or aero-opts {}))]

      (if edn-key (edn-key edn) edn))))
