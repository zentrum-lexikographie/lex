(def version (slurp "../VERSION"))

(defproject zdl-lex-client version
  :description "Provides access to lexikographic resources in Oxygen XML."
  :url "http://example.com/FIXME"

  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.codec "0.1.1"]
                 [com.cemerick/url "0.1.1"
                  :exclusions [[com.cemerick/clojurescript.test]]]
                 [seesaw "1.5.0"]
                 [cprop "0.1.13"]]

  :repositories [["exist" "https://raw.github.com/eXist-db/mvn-repo/master"]
                 ["oxygen" {:url "https://www.oxygenxml.com/maven"
                            :snapshots true}]]

  :jvm-opts ["-Dconf=dev-config.edn"
             "-Dawt.useSystemAAFontSettings=on"
             "-Dswing.aatext=true"]

  :source-paths ["src/clj"]
  :target-path "target/%s"

  :uberjar-name "zdl-lex-client.jar"

  :profiles {:uberjar {:aot :all
                       :dependencies [[nrepl "0.6.0"]
                                      [cider/cider-nrepl "0.21.1"]]}

             :provided {:dependencies [[com.oxygenxml/oxygen-sdk "19.1.0.4"]
                                       [me.flowthing/sigel "0.2.2"]
                                       [me.raynes/fs "1.4.6"]]}}

  :aliases {"package" ["run" "-m" "zdl-lex-client.packaging" :project/version]
            "oxygen" ["run" "-m" "zdl-lex-client.oxygen"]})
