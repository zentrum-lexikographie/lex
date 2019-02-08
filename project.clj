(defproject de.dwds.zdl/oxygen-extensions "2.0-SNAPSHOT"
  :description "Provides access to an XML database of and editing support for lexikographic documents."
  :url "http://example.com/FIXME"
  :license {:name "GPL-3.0"
            :url "https://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.swinglabs.swingx/swingx-all "1.6.5-1"]
                 [org.exist-db/existdb-core "2.2"]
                 [nrepl "0.6.0"]]
  :repositories [["exist" "https://raw.github.com/eXist-db/mvn-repo/master"]
                 ["oxygen" {:url "https://www.oxygenxml.com/maven"
                            :snapshots true}]]
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :target-path "target/%s"
  :uberjar-name "oxygen-extensions.jar"
  :profiles {:uberjar {:aot :all}
             :provided {:dependencies [[com.oxygenxml/oxygen-sdk "20.1.0.1"]]}
             :dev {:source-paths ["src/clj", "src/build"]
                   :dependencies [[me.flowthing/sigel "0.2.2"]
                                  [me.raynes/fs "1.4.6"]]}}
  :aliases {"package" ["run" "-m" "dwdsox.packaging" :project/version]
            "oxygen" ["run", "-m" "dwdsox.oxygen"]}
  :release-tasks [["clean"] ["uberjar"] ["package"]])
