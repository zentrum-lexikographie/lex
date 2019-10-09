(defproject org.zdl.lex/common :lein-v
  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[gremid/lucene-query "0.1.1"]
                 [net.sf.saxon/Saxon-HE "9.9.1-4"]
                 [org.relaxng/jing "_" :exclusions [xml-apis net.sf.saxon/Saxon-HE]]
                 [org.relaxng/trang "_":exclusions [xml-apis net.sf.saxon/Saxon-HE]]
                 [cronjure "0.1.1"]]

  :repl-options {:init-ns zdl-lex-common.dev})

