(defproject org.zdl.lex/build :lein-v
  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[org.zdl.lex/common :version]
                 [me.flowthing/sigel "0.2.2"]]
  :repl-options {:init-ns zdl-lex-build.core}
  :aliases {"build" ["compile"]})
