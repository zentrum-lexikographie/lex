(defproject zdl-lex-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]

                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2"]

                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [ring-logger-timbre "0.7.6"]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]

                 [http-kit "2.2.0"]
                 [hawk "0.2.11"]
                 [me.raynes/fs "1.4.6"]

                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/data.zip "0.1.3"]]
  :main ^:skip-aot zdl-lex-server.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :aliases {"exist2git" ["run", "-m", "zdl-lex-server.exist2git"]})

