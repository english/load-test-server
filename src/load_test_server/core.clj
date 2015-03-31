(ns load-test-server.core
  (:require [org.httpkit.server :as httpkit :refer :all]
            [ring.middleware.reload :as reload]
            [ring.middleware.json :as json-middleware]
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
  {:headers {"Content-Type"       "application/json"
             "GoCardless-Version" "2014-11-03"
             "Authorization"      (str "Basic " (System/getenv "GC_AUTH_TOKEN"))}
   :protocol "https"
   :host "api-staging.gocardless.com"
   :requests {:creditors {:create {:path "/creditors"
                                   :method :post
                                   :body {:creditors {:postal_code "N7 6JT"
                                                      :name "Jamie Testing"
                                                      :city "London"
                                                      :country_code "GB"
                                                      :address_line1 "123 Jamie Street"}}}
                          :index {:path "/creditors"
                                  :method :get}}}})

(defn get-request [config resource action]
  (-> config
      (merge (get-in (:requests config) [resource action]))
      ((fn [{:keys [protocol host path] :as m}]
         (assoc m :url (str protocol "://" host path))))
      ((fn [m]
         (assoc m :body (json/write-str (:body m)))))
      (select-keys [:headers :url :method :body])))

(defn run-load-test [{:keys [resource action duration rate]} conf db blast-fn]
  (let [load-test-id (count @db)
        response-channel (async/chan 1 (comp (map #(assoc % :load-test-id load-test-id :time (System/currentTimeMillis)))
                                             (map #(dissoc % :body))))
        test-request (get-request conf (keyword resource) (keyword action))]
    (async/go-loop
     []
      (when-some [response (async/<! response-channel)]
        (swap! db update-in [load-test-id :data-points] #(conj % response))
        (recur)))
    (swap! db assoc load-test-id {:resource resource :action action :duration duration :rate rate :id load-test-id :data-points #{}})
    (blast-fn test-request response-channel :duration duration :rate rate)))

(defn create-load-test-handler [request]
  (run-load-test (select-keys (:body request) [:resource :action :duration :rate])
                 config load-tests load-test/blast!)
  {:status 201
   :headers {"Access-Control-Allow-Origin" "*"}
   :body ""})

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

(defn resource-actions [config]
  (into {} (map (fn [[resource actions]]
                  {resource (keys actions)})
                (:requests config))))

(def default-duration 5)
(def default-rate 3)

(defn handle-presets [config]
  {:status 200
   :headers {"Access-Control-Allow-Origin" "*"}
   :body {:resources (resource-actions config)
          :url (:host config)
          :duration default-duration
          :rate default-rate}})

(compojure/defroutes all-routes
  (compojure/GET     "/presets"    [] (handle-presets config))
  (compojure/OPTIONS "/load-tests" [] {:status 200
                                       :headers {"Access-Control-Allow-Origin" "*"
                                                 "Access-Control-Allow-Methods" "*"
                                                 "Access-Control-Allow-Headers" "Content-Type"}
                                       :body ""})
  (compojure/GET  "/load-tests" [] list-load-tests-handler)
  (compojure/POST "/load-tests" [] create-load-test-handler))

(defonce server (atom nil))

(def app
  (-> (handler/site #'all-routes)
      reload/wrap-reload
      (json-middleware/wrap-json-body {:keywords? true})
      (json-middleware/wrap-json-response {:pretty true})))

(defn start-server []
  (swap! server (fn [_] (run-server app {:port 3000}))))

(defn kill-server []
  (@server))

(comment (start-server)
         (kill-server))
