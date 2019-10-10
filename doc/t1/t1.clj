(ns t1.t1
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-http.client :as client]))

(defrest
  {"https://api.github.com"
   {"organizations" (list-organizations [since [:maybe pos-int?]] {:basic-auth ["user" "personal-token"]})}})

