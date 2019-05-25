(ns clj-rest-client.core-test
  (:require [clojure.test :refer :all]
            [clj-rest-client.core :as c]
            [clojure.walk :as walk]))

(c/defrest {"abc" {:get (get-0 [])}
            "cde" (get-1 [x any?])
            "yyy" (get-2 [y any?] {::other y})
            "zzz/{mm}/yy" {:get (get-3 [x any? ^:body y any? ^:+ z any? mm any?] {::other z})}
            "xxx" (get-4 [^:form x any? ^:form-map y any? ^:key key-param any? ^:key key-param2 any?])})

(deftest basic-endpoints
  (testing "Basic get endpoint structure"
    (is (= {:request-method :get
            :url "abc"
            :as :json
            ::c/args [] ::c/name `get-0} (get-0)))
    (is (= {:request-method :get
            :query-params {"x" 55}
            :url "cde"
            :as :json
            ::c/args [55] ::c/name `get-1} (get-1 55)))
    (is (= {::other 11
            :request-method :get
            :query-params {"y" 11}
            :url "yyy"
            :as :json
            ::c/args [11] ::c/name `get-2} (get-2 11))))
  (testing "Parameter types"
    (is (= {::other 33
            :request-method :get
            :query-params {"x" 11}
            :body "22"
            :url "zzz/44/yy"
            :content-type :json
            :as :json
            ::c/args [11 22 33 44] ::c/name `get-3} (get-3 11 22 33 44)))
    (is (= {:request-method :get
            :url "xxx"
            :as :json
            :body "{\"key-param\":33,\"key-param2\":44}"
            :form-params {"x" 11 "form-params-x" 22}
            :content-type :json
            ::c/args [11 {:form-params-x 22} 33 44] ::c/name `get-4}
          (get-4 11 {:form-params-x 22} 33 44)))))
