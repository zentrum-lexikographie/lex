(def version (slurp "../VERSION"))

(defproject zdl-lex-common version

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "0.4.490"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [cronjure "0.1.1"]
                 [tick "0.4.10-alpha"
                  :exclusions [cljsjs/js-joda-locale-en-us
                               cljsjs/js-joda-timezone]]
                 [net.sf.saxon/Saxon-HE "9.9.1-4"]]
  :repl-options {:init-ns zdl-lex-common.dev}
  :target-path "target/%s")

