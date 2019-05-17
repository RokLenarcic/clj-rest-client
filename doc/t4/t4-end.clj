(ns t4.t4-end
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-rest-client.conform :refer :all]
            [clj-http.client :as client]
            [clojure.spec.alpha :as s])
  (:import (java.time.format DateTimeFormatter)))

(s/def ::since (->date-format (DateTimeFormatter/ISO_INSTANT)))
(s/def ::all-since #(or (not= (:state %) "all") (:since %)))


(defrest
  {["users/{username}/orgs" string?] {GET (list-user-organizations [])}
   "organizations" {GET (list-organizations [since (s/nilable pos-int?)])}
   ["repos/{owner}/{repo}" string? string?]
   {"assignees" {GET (get-assignees [])}
    "issues" {GET (list-repo-issues ::all-since [state (s/nilable string?) since (s/nilable ::since)])
              ["{issue_no}" pos-int?] {GET (get-issue [])
                                       "assignees" {POST (add-issue-assignees [^:key assignees (s/coll-of string?)])
                                                    DELETE (remove-issue-assignees [^:key assignees (s/coll-of string?)])}}}}})

(defn git-client [url user pass]
  (fn [req]
    (-> req
      (assoc :basic-auth [user pass])
      (update :url #(str url "/" %))
      client/request)))
