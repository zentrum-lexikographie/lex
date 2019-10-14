(defproject org.zdl.lex/common "000000.00.00"
  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[org.clojure/tools.cli "0.4.2"]
                 [gremid/lucene-query "0.1.1"]
                 [net.sf.saxon/Saxon-HE "9.9.1-4"]
                 [org.relaxng/jing "_" :exclusions [xml-apis net.sf.saxon/Saxon-HE]]
                 [org.relaxng/trang "_":exclusions [xml-apis net.sf.saxon/Saxon-HE]]
                 [cronjure "0.1.1"]]

  :repl-options {:init-ns zdl-lex-common.dev}

  :aliases {"build" ["install"]})

