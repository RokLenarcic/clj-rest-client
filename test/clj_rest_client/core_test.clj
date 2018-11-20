(ns clj-rest-client.core-test
  (:require [clojure.test :refer :all]
            [clj-rest-client.core :refer :all]
            [clojure.walk :as walk]))

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