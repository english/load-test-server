(defproject load-test-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [http-kit "2.1.16"]
                 [compojure "1.1.8"]
                 [ring "1.3.1"]
                 [ring/ring-devel "1.1.8"]
                 [cheshire "5.2.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [load-test "0.1.0-SNAPSHOT"]])
