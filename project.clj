(defproject clj-rest-client "1.0.0"
  :description "Thin REST Client layer"
  :url "https://github.com/RokLenarcic/clj-rest-client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [cheshire "5.7.1"]
                 [meta-merge "1.0.0"]]
  :deploy-repositories [["releases" :clojars]]
  :profiles {:dev {:plugins []
                   :global-vars {*warn-on-reflection* true}
                   :dependencies [[clj-http "3.9.0"]]}})
