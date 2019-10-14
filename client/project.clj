(defproject org.zdl.lex/client :lein-v
  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[org.clojure/data.codec "0.1.1"]
                 [org.clojure/core.cache "0.8.1"]
                 [org.clojure/core.memoize "0.7.2"]
                 [com.github.jiconfont/jiconfont-swing "1.0.1"]
                 [com.github.jiconfont/jiconfont-google_material_design_icons "2.2.0.2"]
                 [seesaw "1.5.0"]
                 [org.zdl.lex/common "_" :exclusions [org.relaxng/jing
                                                      org.relaxng/trang]]]

  :repositories [["oxygen" {:url "https://www.oxygenxml.com/maven"
                            :snapshots true}]]

  :repl-options {:init-ns zdl-lex-client.dev}

  :jvm-opts ["-Dawt.useSystemAAFontSettings=on"
             "-Dswing.aatext=true"
             "-Dclojure.compiler.disable-locals-clearing=true"
             "-Dclojure.compiler.elide-meta=[]" 
             "-Dclojure.compiler.direct-linking=false"]

  :compile-path "classes"
  :target-path "../oxygen/plugin/lib"

  :jar-name "org.zdl.lex.client.jar"
  :uberjar-name "org.zdl.lex.client-standalone.jar"

  :clean-targets ^{:protect false} [:compile-path :target-path]

  :profiles {:uberjar
             {:aot :all
              :dependencies [[nrepl "0.6.0"]
                             [cider/cider-nrepl "0.21.1"]]}

             :provided
             {:dependencies [[com.oxygenxml/oxygen-sdk "20.1.0.3"]]}}
  :aliases {"build" ["uberjar"]})
