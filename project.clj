(defproject de.dwds/dwdsox "2.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.swinglabs.swingx/swingx-all "1.6.5-1"]
                 [org.exist-db/existdb-core "2.2"]]
  :repositories [["exist" "https://raw.github.com/eXist-db/mvn-repo/master"]
                 ["oxygen" {:url "https://www.oxygenxml.com/maven"
                            :snapshots true}]]
  :main ^:skip-aot dwdsox.core
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :provided {:dependencies [[com.oxygenxml/oxygen-sdk "20.1.0.1"]]}
             :dev {:dependencies [[me.flowthing/sigel "0.2.2"]]}}
  :aliases {"package" ["run" "-m" "dwdsox.packaging" :project/version]}
  :release-tasks [["uberjar"] ["package"]])
