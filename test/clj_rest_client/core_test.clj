(ns clj-rest-client.core-test
  (:require [clojure.test :refer :all]
            [clj-rest-client.core :refer :all]
            [clojure.walk :as walk]))

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

(deftest parse-vars-test
  (testing "No vars URI"
    (is (= (parse-vars "/some/url") ["/some/url"]))
    (is (= (parse-vars nil) nil)))
  (testing "Urls with vars"
    (is (= (parse-vars "/some/{id}") ["/some/" 'id]))
    (is (= (parse-vars "{id}") ['id]))
    (is (= (parse-vars "aaa{id}bbb{id}ccc") ["aaa" 'id "bbb" 'id "ccc"]))))

(defn derandom-gensym [struct]
  (walk/postwalk
    #(if (and (symbol? %) (.contains (str %) "__auto__"))
       (symbol (.replaceFirst (str %) "\\d{4,}" ""))
       %)
    struct))

(deftest representations-test
  (testing "Empty and no param list should result in the same expand"
    (is (= (derandom-gensym (macroexpand '(defrest {"a" {"b" {GET (t)}}})))
          (derandom-gensym (macroexpand '(defrest {"a" {"b" {GET (t [])}}}))))))
  (testing "Different method specs should result in the same expand"
    (is (= (derandom-gensym (macroexpand '(defrest {"a" {"b" {GET (t [a any?])}}})))
          (derandom-gensym (macroexpand '(defrest {"a" {"b" {:get (t [a any?])}}})))))
    (is (= (derandom-gensym (macroexpand '(defrest {"a" {"b" {GET (t [a any?])}}})))
          (derandom-gensym (macroexpand '(defrest {"a" {"b" {get (t [a any?])}}})))))))