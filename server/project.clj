(defproject org.zdl.lex/server :lein-v
  :plugins [[lein-modules "0.3.11"]
            [lein-resource "17.06.1"]]

  :hooks [leiningen.resource]

  :dependencies [[org.clojure/data.codec "0.1.1"]
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
                 [cheshire "5.9.0"]
                 [clj-http "3.9.1"]
                 [clj-jgit "1.0.0-beta2"]
                 [cpath-clj "0.1.2"]
                 [hiccup "1.0.5"]
                 [ring-logger-timbre "0.7.6"]
                 [ring-webjars "0.2.0"]
                 [org.zdl.lex/common "_"]]

  :compile-path "classes"
  :target-path "../ansible/files/api"

  :jar-name "org.zdl.lex.server.jar"
  :uberjar-name "org.zdl.lex.server-standalone.jar"

  :clean-targets ^{:protect false} [:compile-path :target-path]

  :repl-options {:init-ns zdl-lex-server.core}

  :profiles {:uberjar {:aot :all
                       :main zdl-lex-server.core}
             :project/dev
             {:dependencies [[faker "0.2.2"]]}}
  :resource
  {:resource-paths ["../oxygen"]
   :target-path "classes/oxygen"
   :silent false
   :verbose false
   :skip-stencil [ #".*" ]}
  :aliases {"build" ["uberjar"]})

