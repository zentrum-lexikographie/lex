(def version (slurp "../VERSION"))

(defproject zdl-lex-client version
  :description "Provides access to lexikographic resources in Oxygen XML."
  :url "http://example.com/FIXME"

  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.codec "0.1.1"]
                 [seesaw "1.5.0"]
                 [pathetic "0.5.1"]
                 [cprop "0.1.13"]]

  :repositories [["oxygen" {:url "https://www.oxygenxml.com/maven"
                            :snapshots true}]]

  :plugins [[lein-exec "0.3.7"]]

  :jvm-opts ["-Dconf=dev-config.edn"
             "-Dawt.useSystemAAFontSettings=on"
             "-Dswing.aatext=true"]

  :source-paths ["src/clj"]
  :target-path "target/%s"

  :uberjar-name "zdl-lex-client.jar"

  :profiles {:uberjar
             {:aot :all
              :dependencies [[nrepl "0.6.0"]
                             [cider/cider-nrepl "0.21.1"]]}

             :dev
             {:dependencies [[me.raynes/fs "1.4.6"]
                             [me.flowthing/sigel "0.2.2"]]}

             :provided
             {:dependencies [[com.oxygenxml/oxygen-sdk "19.1.0.4"]]}}

  :aliases {"package" ["exec" "-p" "scripts/package.clj" :project/version]
            "oxygen" ["exec" "-p" "scripts/oxygen.clj"]})
