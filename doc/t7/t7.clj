(ns t7.t7
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-rest-client.transform :refer :all]
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

(def get-api-map
  `{"issues" (list-issues ~issue-query)
    "user/orgs" (list-my-orgs [])
    "user/issues" (list-my-issues ~issue-query)
    "users/{username}/orgs" (list-user-organizations [username string?])
    "orgs/{org}/issues" (list-org-issues [org string? ~@issue-query])
    "organizations" (list-organizations [since (s/nilable pos-int?)])
    "repos/{owner}/{repo}/branches" (list-repo-branches [owner string? repo string?])
    "repos/{owner}/{repo}/assignees" (list-assignees [owner string? repo string?])
    "repos/{owner}/{repo}/issues" (list-repo-issues
                                    ::all-since ~(list-params
                                                   '[owner string?
                                                     repo string?
                                                     &
                                                     filter ::issue-filter
                                                     milestone ::milestone
                                                     state ::issue-state-filter
                                                     assignee string?
                                                     creator string?
                                                     mentioned string?
                                                     labels ::labels]
                                                   [:created :updated :comments]))
    "repos/{owner}/{repo}/issues/{issue-no}" (get-issue [owner string? repo string? issue-no pos-int?])})

(def post-api-map
  '{"repos/{owner}/{repo}/issues/{issue-no}/assignees"
    (add-issue-assignees [owner string? repo string? issue-no pos-int? ^:key assignees (s/coll-of string?)])})

(def delete-api-map
  '{"repos/{owner}/{repo}/issues/{issue-no}/assignees"
    (remove-issue-assignees [owner string? repo string? issue-no pos-int? ^:key assignees (s/coll-of string?)])})

(defrest get-api-map :default-method :get)
(defrest post-api-map :default-method :post)
(defrest delete-api-map :default-method :delete)

(defn git-client [url user pass]
  (fn [req]
    (-> req
      (assoc :basic-auth [user pass])
      (update :url #(str url "/" %))
      client/request)))
