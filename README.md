# load-test-server

API for https://github.com/english/load-test-client.


## Run a load test
```http
POST /load-tests
Content-Type application/json

{
  "resource": "creditors",
  "action": "index",
  "duration": 5,
  "rate": 2
}
```

## Read load-test results
```http
GET /load-tests
Upgrade: websocket
Connection: Upgrade
...
```

```js
{
  "id": 0,
  "resource": "creditors",
  "action": "index",
  "duration": 5,
  "rate": 2,
  "data-points": [{
    "response-time": 120,
    "status": 200,
    "method": "GET",
    "url": "http://example.com",
    "body": "some body"
  }]
}
...
```

The above will receive all load tests and then every new version of a load test
created.

## Notes

### Config ideas

Store endpoints and payloads in a huge map, each specifying dependencies.
```clojure
(def foo
  {:headers {"Content-Type"       "application/json"
             "GoCardless-Version" "2014-11-03"
             "Authorization"      (str "Basic " (System/getenv "GC_AUTH_TOKEN"))}
   :protocol "https"
   :host "api-staging.gocardless.com"
   :requests {:creditors {:create {:path "/creditors"
                                   :method :post
                                   :body {:creditors {:postal_code   "N7 6JT"
                                                      :name          "Jamie Testing"
                                                      :city          "London"
                                                      :country_code  "GB"
                                                      :address_line1 "123 Jamie Street"}}}
                          :index  {:path "/creditors"
                                   :method :get}
                          :show   {:path "/creditors/:id"
                                   :method :get
                                   :dependency {:resource     :creditors
                                                :action       :create
                                                :url-replace  :id
                                                :extract-path [:creditors :id]}}}
              :creditor-bank-accounts {:create {:path "/creditor-bank-accounts"
                                                :method :post
                                                :body {:creditor_bank_accounts {:account_number      "55779911"
                                                                                :sort_code           "200000"
                                                                                :country_code        "GB"
                                                                                :account_holder_name "Nude Wines"
                                                                                :set_as_defaul_payout_account true
                                                                                :links {:creditor ":creditor_id"}}}
                                                :dependency {:resource     :creditors
                                                             :action       :create
                                                             :body-path    [:links :creditor]
                                                             :extract-path [:creditors :id]}}}}})
```

## License

Copyright © 2015 Jamie English

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
