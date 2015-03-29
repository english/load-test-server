(ns load-test-server.core-test
  (:require [clojure.test :refer :all]
            [load-test-server.core :refer :all]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]))

(deftest presets-test
  (testing "GET /presets"
    (let [{:keys [status headers body]} ((app) (mock/request :get "/presets"))]
      (is (= 200 status))
      (is (= "application/json; charset=utf-8" (get headers "Content-Type")))
      (is (= {:resources {:creditors ["index" "create"]}
              :url "api-staging.gocardless.com"
              :duration 5
              :rate 3}
             (json/read-str body :key-fn keyword))))))
