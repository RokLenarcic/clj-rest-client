(ns t2.t2
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-http.client :as client]))

(defrest
  {"https://api.github.com"
   {["users/{username}/orgs" string?] {GET (list-user-organizations [])}
    "organizations" {GET (list-organizations [since [:maybe pos-int?]])}
    ["repos/{owner}/{repo}" string? string?]
    {"assignees" {GET (list-assignees [])}
     "issues" {["{issue_no}" pos-int?] {GET (get-issue [])
                                        "assignees" {POST (add-issue-assignees [^:key assignees [:sequential string?]])
                                                     DELETE (remove-issue-assignees [^:key assignees [:sequential string?]])}}}}}}
  :defaults {:basic-auth ["user" "personal-token"]})
