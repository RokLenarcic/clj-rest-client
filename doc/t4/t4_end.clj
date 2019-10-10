(ns t4.t4-end
  (:require [clj-rest-client.core :refer [defrest]]
            [clj-http.client :as client]
            [malli.transform :as mt]))

(def xf
  (mt/transformer
    {:name :rest-client
     :encoders (select-keys mt/+string-encoders+ ['inst?])
     :decoders (select-keys mt/+string-decoders+ ['inst?])}))

(def filtered-function [:fn {:error/message "filters that target state \"all\" must limit by \"since\""}
                        '(fn [{:keys [since state]}] (or (not= state "all") since))])

(defrest
  {["users/{username}/orgs" string?] (list-user-organizations [])
   "organizations" (list-organizations [since [:maybe pos-int?]])
   ["repos/{owner}/{repo}" string? string?]
   {"assignees" (list-assignees [])
    "issues" {GET (list-repo-issues filtered-function [state [:maybe string?] since [:maybe inst?]])
              ["{issue_no}" pos-int?] {GET (get-issue [])
                                       "assignees" {POST (add-issue-assignees [^:key assignees [:sequential string?]])
                                                    DELETE (remove-issue-assignees [^:key assignees [:sequential string?]])}}}}}
  :transformer xf)

(defn git-client [url user pass]
  (fn [req]
    (-> req
        (assoc :basic-auth [user pass])
        (update :url #(str url "/" %))
        client/request)))
