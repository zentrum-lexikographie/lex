(def version (slurp "../VERSION"))

(defproject zdl-lex-client version
  :description "Provides access to lexikographic resources in Oxygen XML."
  :url "http://example.com/FIXME"

   :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/core.memoize "0.7.2"]
   [com.github.jiconfont/jiconfont-swing "1.0.1"]
   [com.github.jiconfont/jiconfont-google_material_design_icons "2.2.0.2"]
   [diehard "0.8.4"]
   [etaoin "0.3.5"]
   [seesaw "1.5.0"]
   [pathetic "0.5.1"]
   [zdl-lex-common ~version]]

  :repositories [["oxygen" {:url "https://www.oxygenxml.com/maven"
                            :snapshots true}]]

  :plugins [[lein-exec "0.3.7"]
            [lein-environ "1.1.0"]]

  :repl-options {:init-ns zdl-lex-client.dev}

  :jvm-opts ["-Dawt.useSystemAAFontSettings=on"
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

             :dev [:project/dev :profiles/dev]
             :project/dev {}

             :provided
             {:dependencies [[com.oxygenxml/oxygen-sdk "20.1.0.3"]]}})
