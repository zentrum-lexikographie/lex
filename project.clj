(defproject de.dwds.zdl/oxygen-extensions "2.0-SNAPSHOT"
  :description "Provides access to an XML database of and editing support for lexikographic documents."
  :url "http://example.com/FIXME"
  :license {:name "GPL-3.0"
            :url "https://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [seesaw "1.5.0"]
                 [aero "1.1.3"]
                 [org.swinglabs.swingx/swingx-all "1.6.5-1"]
                 [org.clojure/data.codec "0.1.1"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.exist-db/existdb-core "2.2"]]
  :repositories [["exist" "https://raw.github.com/eXist-db/mvn-repo/master"]
                 ["oxygen" {:url "https://www.oxygenxml.com/maven"
                            :snapshots true}]]
  :plugins [[lein-exec "0.3.7"]]
  :jvm-opts ["-Dawt.useSystemAAFontSettings=on" "-Dswing.aatext=true"]
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :target-path "target/%s"
  :uberjar-name "oxygen-extensions.jar"
  :profiles {:uberjar {:aot :all
                       :dependencies [[nrepl "0.6.0"]]}
             :provided {:dependencies [[com.oxygenxml/oxygen-sdk "20.1.0.1"]]}
             :dev {:dependencies [[me.flowthing/sigel "0.2.2"]
                                  [me.raynes/fs "1.4.6"]]}}
  :aliases {"facets" ["exec", "-p" "scripts/facets.clj"]
            "package" ["exec" "-p" "scripts/package.clj" :project/version]
            "oxygen" ["exec", "-p" "scripts/oxygen.clj"]}
  :release-tasks [["clean"] ["uberjar"] ["package"]])
