(ns load-test-server.core
  (:require [org.httpkit.server :as httpkit :refer :all]
            [ring.util.response :as ring-response]
            [ring.middleware.reload :as reload]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.core :as compojure]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [clojure.set :as clj-set]
            [load-test.core :as load-test]))

;; {:id "LT123" :resource "Creditors" :action "Create"}
(def load-tests (atom #{}))

;; {:id "DP123" :load-test "LT123" :response-time 201 :status 201}}
(def data-points (atom #{}))

(def config
  {:headers {"Content-Type" "application/json"
             "GoCardless-Version" "2014-11-03"
             "Authorization" (str "Basic " (System/getenv "GC_AUTH_TOKEN"))}
   :protocol "https"
   :host "api-staging.gocardless.com"
   :requests {:creditors {:create {:path "/creditors"
                                   :method :post
                                   :body "{\"creditors\":{\"postal_code\":\"N7 6JT\",\"name\":\"Jamie Testing\",\"city\":\"London\",\"country_code\":\"GB\",\"address_line1\":\"123 Jamie Street\"}}"}
                          :index {:path "/creditors"
                                  :method :get}}}})

(defn get-request [config resource action]
  (-> config
      (merge (get-in (:requests config) [resource action]))
      ((fn [m]
         (assoc m :url (str (:protocol m) "://" (:host m) (:path m)))))
      (select-keys [:headers :url :method :body])))

(defn run-load-test [resource action duration rate]
  (let [load-test-id (count @load-tests)
        response-channel (async/chan 1 (map #(assoc % :load-test-id load-test-id :time (System/currentTimeMillis))))
        request (get-request config resource action)]
    (async/go-loop
      []
      (when-some [response (async/<! response-channel)]
        (swap! data-points conj response)
        (recur)))

    (swap! load-tests conj {:resource resource :action action :duration duration :rate rate :id load-test-id})
    (load-test/blast! request response-channel :duration duration :rate rate)))

(defn load-tests-handler [request]
  (httpkit/with-channel request channel
    (add-watch load-tests :new-load-tests
               (fn [_ _ old-state new-state]
                 (httpkit/send! channel (json/write-str (clj-set/difference new-state old-state)))))

    (doseq [load-test @load-tests]
      (httpkit/send! channel (json/write-str load-test)))))

(defn data-points-handler [request]
  (httpkit/with-channel request channel
    (add-watch data-points :new-data-points
               (fn [_ _ old-state new-state]
                 (httpkit/send! channel (json/write-str (clj-set/difference new-state old-state)))))

    (doseq [data-point @data-points]
      (httpkit/send! channel (json/write-str data-point)))))

(compojure/defroutes all-routes
  (compojure/GET "/load-tests" [] load-tests-handler)
  (compojure/GET "/data-points" [] data-points-handler)
  (compojure/POST "/trigger" [resource action duration rate]
                  (run-load-test (keyword resource) (keyword action) (Integer/parseInt duration) (Integer/parseInt rate))
                  "Success")
  (route/not-found "<p>Page not found</p>"))

(defonce server (atom nil))

(defn app []
  (-> (handler/site #'all-routes)
      reload/wrap-reload
      (run-server {:port 3000})))

(defn start-server []
  (swap! server (fn [_] (app))))

(defn kill-server []
  (@server))

(comment (start-server)
         (kill-server))
