(ns load-test-server.core-test
  (:require [clojure.test :refer :all]
            [load-test-server.core :as core]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]))

(def test-config
  {:headers {"Content-Type" "application/json"}
   :protocol "https"
   :host "api.example.com"
   :requests {:people {:create {:path "/people"
                                :method :post
                                :body "{\"people\":{\"name\":\"Jamie\",\"city\":\"London\"}}"}
                       :index {:path "/people"
                               :method :get}}}})

(deftest handle-presets-test
  (testing "parses config correctly"
    (let [{:keys [status headers body] :as resp} (core/handle-presets test-config)]
      (is (= 200 status))
      #_(is (= "application/json; charset=utf-8" (get headers "Content-Type")))
      (is (= {:resources {:people '(:index :create)}
              :url "api.example.com"
              :duration 5
              :rate 3}
             body)))))

(deftest run-load-test-test
  (testing "blaster gets called with correct args"
    (let [request-body {:resource :people
                        :action :create
                        :duration 10
                        :rate 4}
          config test-config
          db (atom {})
          blaster (fn [test-request response-channel & {:keys [duration rate]}]
                    (is (= {:url "https://api.example.com/people"
                            :method :post
                            :headers {"Content-Type" "application/json"}
                            :body "{\"people\":{\"name\":\"Jamie\",\"city\":\"London\"}}"}
                           test-request))
                    (is (= 10 duration))
                    (is (= 4 rate)))]
      (core/run-load-test request-body config db blaster))))
