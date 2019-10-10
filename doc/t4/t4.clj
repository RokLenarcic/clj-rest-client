(ns t4.t4
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-http.client :as client]))

(defrest
  {["users/{username}/orgs" string?] (list-user-organizations [])
   "organizations" (list-organizations [since [:maybe pos-int?]])
   ["repos/{owner}/{repo}" string? string?]
   {"assignees" (list-assignees [])
    "issues" {["{issue_no}" pos-int?] {GET (get-issue [])
                                       "assignees" {POST (add-issue-assignees [^:key assignees [:sequential string?]])
                                                    DELETE (remove-issue-assignees [^:key assignees [:sequential string?]])}}}}})

(defn git-client [url user pass]
  (fn [req]
    (-> req
      (assoc :basic-auth [user pass])
      (update :url #(str url "/" %))
      client/request)))
