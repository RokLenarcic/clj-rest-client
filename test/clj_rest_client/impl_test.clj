(ns clj-rest-client.impl-test
  (:require [clojure.test :refer :all]
            [clj-rest-client.impl :refer :all]))

(deftest parse-vars-test
  (testing "No vars URI"
    (is (= (parse-uri "/some/url") ["/some/url"]))
    (is (= (parse-uri nil) nil)))
  (testing "Urls with vars"
    (is (= (parse-uri "/some/{id}") ["/some/" 'id]))
    (is (= (parse-uri "{id}") ['id]))
    (is (= (parse-uri "aaa{id}bbb{id}ccc") ["aaa" 'id "bbb" 'id "ccc"]))))

(deftest merge-maps-test
  (testing "Merging scalars takes last"
    (is (= (merge-maps "A" 1) 1))
    (is (= (merge-maps nil 2) 2))
    (is (= (merge-maps 3 nil) nil)))
  (testing "Maps should merge as union"
    (is (= (merge-maps {:a 1 :b 2} {:c 3 :d 4}) {:a 1 :b 2 :c 3 :d 4}))
    (is (= (merge-maps {:a 2 :b 3} {:a 1 :c 2}) {:a 1 :b 3 :c 2})))
  (testing "Nil map values work"
    (is (= (merge-maps {:a 1} {:a nil} {:a nil}))))
  (testing "Maps should merge recursively"
    (is (= (merge-maps {:a {:b {:c 1}}} {:a {:b {:d 4} :e 5}}) {:a {:b {:c 1 :d 4} :e 5}}))))
