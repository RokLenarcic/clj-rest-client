(ns t1.t1
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-http.client :as client]
            [clojure.spec.alpha :as s]))

(defrest
  {"https://api.github.com"
   {"organizations" (list-organizations [since (s/nilable pos-int?)] {:basic-auth ["user" "personal-token"]})}})

