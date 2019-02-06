(defproject dwdsox "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :main ^:skip-aot dwdsox.core
  :java-source-paths ["src"]
  :target-path "target/%s"
  :plugins [[lein-pprint "1.2.0"]]
  :profiles {:uberjar {:aot :all}})
