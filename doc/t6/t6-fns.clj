(ns t6.t6-fns
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-rest-client.conform :refer :all]
            [clj-http.client :as client]
            [clojure.spec.alpha :as s])
  (:import (java.time.format DateTimeFormatter)))

(s/def ::issue-filter #{:assigned :created :mentioned :all :subscribed})
(s/def ::since (->date-format (DateTimeFormatter/ISO_INSTANT)))
(s/def ::all-since #(or (not= (:state %) :all) (:since %)))
(s/def ::milestone (s/nonconforming (s/or :i int? :s string?)))
(s/def ::issue-state-filter #{:open :closed :all})
(s/def ::labels (s/and (s/coll-of string?) (s/conformer #(clojure.string/join "," %))))
(s/def ::sort-dir #{:asc :desc})

(defn list-params [v sort-cols]
  (into v
    (concat (if (not-any? #(= % '&) v) ['&] [])
      ['sort (apply hash-set sort-cols)
       'direction ::sort-dir
       'since ::since])))

(def issue-query
  (list-params
    '[& filter ::issue-filter state ::issue-state-filter labels ::labels]
    [:created :updated :comments]))

(def issue-assignees-endpoints
  `{POST (add-issue-assignees [^:key assignees (s/coll-of string?)])
    DELETE (remove-issue-assignees [^:key assignees (s/coll-of string?)])})

(def repo-issue-endpoints
  `{GET (list-repo-issues ::all-since ~(list-params
                                         '[&
                                           filter ::issue-filter
                                           milestone ::milestone
                                           state ::issue-state-filter
                                           assignee string?
                                           creator string?
                                           mentioned string?
                                           labels ::labels]
                                         [:created :updated :comments]))
    ["{issue_no}" pos-int?] {GET (get-issue [])
                             "assignees" ~issue-assignees-endpoints}})

(def repo-endpoints
  `{"branches" {GET (get-repo-branches [])}
    "assignees" {GET (get-assignees [])}
    "issues" ~repo-issue-endpoints})

(def user-endpoints
  `{"orgs" {GET (list-my-orgs [])}
    "issues" {GET (list-my-issues ~issue-query)}})

(def api-map
  `{"issues" {GET (list-issues ~issue-query)}
    "user" ~user-endpoints
    "orgs/{org}/issues" {GET (list-org-issues [org string? ~@issue-query])}
    "organizations" {GET (list-organizations [since (s/nilable pos-int?)])}
    ["users/{username}/orgs" string?] {GET (list-user-organizations [])}
    ["repos/{owner}/{repo}" string? string?] ~repo-endpoints})

(defrest api-map)

(defn git-client [url user pass]
  (fn [req]
    (-> req
      (assoc :basic-auth [user pass])
      (update :url #(str url "/" %))
      client/request)))
