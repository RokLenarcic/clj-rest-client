(defproject clj-rest-client "1.0.0-beta3"
  :description "Thin REST Client layer"
  :url "https://github.com/RokLenarcic/clj-rest-client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [cheshire "5.7.1"]]
  :deploy-repositories [["releases" :clojars]]
  :profiles {:dev {:plugins []
                   :jvm-opts ^:replace ["-server"]
                   :global-vars {*warn-on-reflection* true}
                   :dependencies []}})
