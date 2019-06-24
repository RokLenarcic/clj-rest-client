(ns t2.t2
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-http.client :as client]
            [clojure.spec.alpha :as s]))

(defrest
  {"https://api.github.com"
   {["users/{username}/orgs" string?] {GET (list-user-organizations [])}
    "organizations" {GET (list-organizations [since (s/nilable pos-int?)])}
    ["repos/{owner}/{repo}" string? string?]
    {"assignees" {GET (list-assignees [])}
     "issues" {["{issue_no}" pos-int?] {GET (get-issue [])
                                        "assignees" {POST (add-issue-assignees [^:key assignees (s/coll-of string?)])
                                                     DELETE (remove-issue-assignees [^:key assignees (s/coll-of string?)])}}}}}}
  :defaults {:basic-auth ["user" "personal-token"]})
