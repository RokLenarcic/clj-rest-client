(ns t6.t6-keyworded
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-rest-client.conform :refer :all]
            [clj-http.client :as client]
            [clojure.spec.alpha :as s])
  (:import (java.time.format DateTimeFormatter)))

(s/def ::since (->date-format (DateTimeFormatter/ISO_INSTANT)))
(s/def ::all-since #(or (not= (:state %) "all") (:since %)))
(s/def ::milestone (s/nonconforming (s/or :i int? :s string?)))
(s/def ::issue-state-filter #{"open" "closed" "all"})
(s/def ::labels (s/and (s/coll-of string?) (s/conformer #(clojure.string/join "," %))))
(s/def ::sort-dir #{"asc" "desc"})

(def issue-endpoints
  {:GET [:get-issue []]
   "assignees" {:POST [:add-issue-assignees [[:key :assignees] `(s/coll-of string?)]]
                :DELETE [:remove-issue-assignees [[:key :assignees] `(s/coll-of string?)]]}})

(def repo-endpoints
  {"assignees" [:list-assignees []]
   "issues" {:GET [:list-repo-issues ::all-since
                   [:&
                    :milestone ::milestone
                    :state ::issue-state-filter
                    :assignee string?
                    :creator string?
                    :mentioned string?
                    :labels ::labels
                    :sort #{"created" "updated" "comments"}
                    :direction ::sort-dir
                    :since ::since]]
             ["{issue_no}" pos-int?] issue-endpoints}})

(def api-map
  {"organizations" [:list-organizations [:since `(s/nilable pos-int?)]]
   ["users/{username}/orgs" string?] [:list-user-organizations []]
   ["repos/{owner}/{repo}" string? string?] repo-endpoints})

(defrest api-map)

(defn git-client [url user pass]
  (fn [req]
    (-> req
      (assoc :basic-auth [user pass])
      (update :url #(str url "/" %))
      client/request)))
