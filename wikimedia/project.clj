(def version (slurp "../VERSION"))

(defproject zdl-lex-wikimedia version
  :description "Parses (German) Wiktionary dumps."
  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/data.zip "0.1.3"]
                 [org.clojure/data.csv "0.1.4"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [org.slf4j/slf4j-api "1.7.26"]
                 [org.slf4j/jul-to-slf4j "1.7.26"]
                 [org.slf4j/jcl-over-slf4j "1.7.26"]
                 [org.sweble.wikitext/swc-parser-lazy "3.1.9"]
                 [org.dkpro.jwktl/dkpro-jwktl "1.1.1-SNAPSHOT"]
                 [org.apache.jena/jena-arq "3.12.0"]]
  :global-vars {*warn-on-reflection* false}
  :main ^:skip-aot zdl-lex-wikimedia.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
