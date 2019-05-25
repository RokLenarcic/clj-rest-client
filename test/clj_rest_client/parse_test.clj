(ns clj-rest-client.parse-test
  (:require [clojure.test :refer :all]
            [clj-rest-client.parse :refer :all]))

(deftest parse-vars-test
  (testing "No vars URI"
    (is (= (parse-uri "/some/url") ["/some/url"]))
    (is (= (parse-uri nil) nil)))
  (testing "Urls with vars"
    (is (= (parse-uri "/some/{id}") ["/some/" :id]))
    (is (= (parse-uri "{id}") [:id]))
    (is (= (parse-uri "aaa{id}bbb{id}ccc") ["aaa" :id "bbb" :id "ccc"]))))
