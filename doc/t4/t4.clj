(ns t4.t4
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-rest-client.conform :refer :all]
            [clj-http.client :as client]
            [clojure.spec.alpha :as s]))

(defrest
  {["users/{username}/orgs" string?] (list-user-organizations [])
   "organizations" (list-organizations [since (s/nilable pos-int?)])
   ["repos/{owner}/{repo}" string? string?]
   {"assignees" (list-assignees [])
    "issues" {["{issue_no}" pos-int?] {GET (get-issue [])
                                       "assignees" {POST (add-issue-assignees [^:key assignees (s/coll-of string?)])
                                                    DELETE (remove-issue-assignees [^:key assignees (s/coll-of string?)])}}}}})

(defn git-client [url user pass]
  (fn [req]
    (-> req
      (assoc :basic-auth [user pass])
      (update :url #(str url "/" %))
      client/request)))
