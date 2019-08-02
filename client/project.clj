(def version (slurp "../VERSION"))

(defproject zdl-lex-client version
  :description "Provides access to lexikographic resources in Oxygen XML."
  :url "http://example.com/FIXME"

  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/data.codec "0.1.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [mount "0.1.16"]
                 [seesaw "1.5.0"]
                 [pathetic "0.5.1"]
                 [cprop "0.1.13"]
                 [cronjure "0.1.1"]
                 [tick "0.4.10-alpha" :exclusions [cljsjs/js-joda-locale-en-us
                                                   cljsjs/js-joda-timezone]]
                 [com.github.jiconfont/jiconfont-swing "1.0.1"]
                 [com.github.jiconfont/jiconfont-google_material_design_icons "2.2.0.2"]
                 [gremid/lucene-query "0.1.1"]]

  :repositories [["oxygen" {:url "https://www.oxygenxml.com/maven"
                            :snapshots true}]]

  :plugins [[lein-exec "0.3.7"]]

  :repl-options {:init-ns zdl-lex-client.dev}

  :jvm-opts ["-Dconf=dev-config.edn"
             "-Dawt.useSystemAAFontSettings=on"
             "-Dswing.aatext=true"
             "-Dclojure.compiler.disable-locals-clearing=true"
             "-Dclojure.compiler.elide-meta=[]" 
             "-Dclojure.compiler.direct-linking=false"]

  :source-paths ["src/clj"]
  :target-path "target/%s"

  :uberjar-name "zdl-lex-client.jar"

  :profiles {:uberjar
             {:aot :all
              :dependencies [[nrepl "0.6.0"]
                             [cider/cider-nrepl "0.21.1"]]}

             :dev
             {:dependencies [[me.raynes/fs "1.4.6"]
                             [me.flowthing/sigel "0.2.2"]
                             [org.apache.commons/commons-compress "1.18"]]}

             :provided
             {:dependencies [[com.oxygenxml/oxygen-sdk "20.1.0.3"]]}}

  :aliases {"package" ["exec" "-p" "scripts/package.clj" :project/version]})
