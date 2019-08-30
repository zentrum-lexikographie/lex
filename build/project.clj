(def version (slurp "../VERSION"))

(defproject zdl-lex-build version
  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :license {:name "LGPL-3.0"
            :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [me.flowthing/sigel "0.2.2"]
                 [zdl-lex-common ~version]]
  :repl-options {:init-ns zdl-lex-build.core})
