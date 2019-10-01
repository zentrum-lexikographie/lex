(def version (slurp "../VERSION"))

(defproject zdl-lex-common version

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/data.codec "0.1.1"]
                 [org.clojure/tools.cli "0.4.2"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [com.taoensso/timbre "4.10.0"]
                 [gremid/lucene-query "0.1.1"]
                 [me.raynes/fs "1.4.6"]
                 [metosin/spec-tools "0.10.0"]
                 [net.sf.saxon/Saxon-HE "9.9.1-4"]
                 [org.relaxng/jing "20181222"
                  :exclusions [xml-apis net.sf.saxon/Saxon-HE]]
                 [org.relaxng/trang "20181222"
                  :exclusions [xml-apis net.sf.saxon/Saxon-HE]]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [cronjure "0.1.1"]
                 [environ "1.1.0"]
                 [mount "0.1.16"]
                 [tick "0.4.10-alpha"
                  :exclusions [cljsjs/js-joda-locale-en-us
                               cljsjs/js-joda-timezone]]]
  :repl-options {:init-ns zdl-lex-common.dev}
  :target-path "target/%s"
  :plugins [[lein-environ "1.1.0"]])

