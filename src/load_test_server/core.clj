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

;; TODO: Replace with DB!
(def load-tests (atom {}))

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
      ((fn [{:keys [protocol host path] :as m}]
         (assoc m :url (str protocol "://" host path))))
      (select-keys [:headers :url :method :body])))

(defn create-load-test-handler [request]
  (let [{:keys [resource action duration rate]} (json/read-str (slurp (:body request)) :key-fn keyword)
        load-test-id (count @load-tests)
        response-channel (async/chan 1 (comp (map #(assoc % :load-test-id load-test-id :time (System/currentTimeMillis)))
                                             (map #(dissoc % :body))))
        test-request (get-request config (keyword resource) (keyword action))]
    (async/go-loop
      []
      (when-some [response (async/<! response-channel)]
        (swap! load-tests update-in [load-test-id :data-points] #(conj % response))
        (recur)))

    (swap! load-tests assoc load-test-id {:resource resource :action action :duration duration :rate rate :id load-test-id :data-points #{}})
    (load-test/blast! test-request response-channel :duration duration :rate rate)
    {:status 201
     :headers {"Access-Control-Allow-Origin" "*"}
     :body ""}))

(defn index-by [coll key-fn]
  (into {} (map (juxt key-fn identity) coll)))

(defn list-load-tests-handler [request]
  (httpkit/with-channel request channel
    (add-watch load-tests :new-load-tests
               (fn [_ _ old-state new-state]
                 (let [load-test-diff (index-by (clj-set/difference (set (vals new-state))
                                                                    (set (vals old-state)))
                                                :id)]
                   (httpkit/send! channel (json/write-str load-test-diff)))))
    (httpkit/send! channel (json/write-str @load-tests))))

(defn handle-presets [request]
  (-> {:resources (into {} (map (fn [[resource actions]]
                                  {resource (keys actions)})
                                (:requests config)))
       :url (:host config)}
      (json/write-str)
      (ring-response/response)
      (ring-response/header "Content-Type" "application/json")
      (ring-response/header "Access-Control-Allow-Origin" "*")))

(compojure/defroutes all-routes
  (compojure/GET "/presets" [] handle-presets)
  (compojure/OPTIONS "/load-tests" [] {:status 200
                                       :headers {"Access-Control-Allow-Origin" "*"
                                                 "Access-Control-Allow-Methods" "*"
                                                 "Access-Control-Allow-Headers" "Content-Type"}
                                       :body ""})
  (compojure/GET "/load-tests" [] list-load-tests-handler)
  (compojure/POST "/load-tests" [] create-load-test-handler)
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
