(def version (slurp "../VERSION"))

(defproject zdl-lex-server version

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/data.zip "0.1.3"]
                 [com.h2database/h2 "1.4.199"]
                 [com.layerware/hugsql "0.4.9"]
                 [io.xapix/clj-soap "1.1.0"]
                 [metosin/muuntaja "0.6.3"]
                 [metosin/reitit "0.3.9"]
                 [metosin/ring-http-response "0.9.1"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [org.webjars.npm/bulma "0.7.4"]
                 [org.webjars.npm/material-icons "0.3.0"]
                 [org.webjars/webjars-locator "0.36"]
                 [cheshire "5.8.1"]
                 [clj-http "3.9.1"]
                 [clj-jgit "1.0.0-beta2"]
                 [hiccup "1.0.5"]
                 [ring-logger-timbre "0.7.6"]
                 [ring-webjars "0.2.0"]
                 [zdl-lex-common ~version]]

  :main ^:skip-aot zdl-lex-server.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev [:project/dev :profiles/dev]
             :project/dev
             {:dependencies [[midje "1.9.8"]
                             [faker "0.2.2"]]}}
  :plugins [[lein-environ "1.1.0"]])

