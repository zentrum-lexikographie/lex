(def version (slurp "../VERSION"))

(defproject zdl-lex-server version

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/data.zip "0.1.3"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring-logger-timbre "0.7.6"]
                 [ring-webjars "0.2.0"]
                 [metosin/muuntaja "0.6.3"]
                 [metosin/reitit "0.3.1"]
                 [metosin/ring-http-response "0.9.1"]
                 [org.webjars.npm/bulma "0.7.4"]
                 [org.webjars.npm/material-icons "0.3.0"]
                 [org.webjars/webjars-locator "0.36"]
                 [http-kit "2.3.0"]
                 [clj-http "3.9.1"]
                 [clj-jgit "0.8.10"]
                 [cheshire "5.8.1"]
                 [tick "0.4.10-alpha" :exclusions [cljsjs/js-joda-locale-en-us
                                                   cljsjs/js-joda-timezone]]
                 [mount "0.1.16"]
                 [cprop "0.1.13"]
                 [me.raynes/fs "1.4.6"]
                 [com.climate/claypoole "1.1.4"]
                 [hiccup "1.0.5"]
                 [gremid/lucene-query "0.1.0"]]

  :jvm-opts ["-Dconf=dev-config.edn"]
  :main ^:skip-aot zdl-lex-server.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

