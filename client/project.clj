(def version (slurp "../VERSION"))

(defproject zdl-lex-client version
  :description "Provides access to lexikographic resources in Oxygen XML."
  :url "http://example.com/FIXME"

  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.codec "0.1.1"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.swinglabs.swingx/swingx-all "1.6.5-1"]
                 [org.exist-db/existdb-core "2.2"]
                 [seesaw "1.5.0"]
                 [cprop "0.1.13"]]

  :repositories [["exist" "https://raw.github.com/eXist-db/mvn-repo/master"]
                 ["oxygen" {:url "https://www.oxygenxml.com/maven"
                            :snapshots true}]]

  :jvm-opts ["-Dconf=dev-config.edn"
             "-Dawt.useSystemAAFontSettings=on"
             "-Dswing.aatext=true"]

  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :target-path "target/%s"

  :uberjar-name "zdl-lex-client.jar"

  :profiles {:uberjar {:aot :all
                       :dependencies [[nrepl "0.6.0"]]}

             :provided {:dependencies [[com.oxygenxml/oxygen-sdk "20.1.0.1"]
                                       [me.flowthing/sigel "0.2.2"]
                                       [me.raynes/fs "1.4.6"]]}}

  :aliases {"package" ["run" "-m" "zdl-lex-client.packaging" :project/version]
            "oxygen" ["run" "-m" "zdl-lex-client.oxygen"]})
