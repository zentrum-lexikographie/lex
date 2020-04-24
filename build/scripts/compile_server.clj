(def compiled-ns
  ['clojure.tools.logging.impl
   'zdl.lex.server])

(doseq [ns compiled-ns] (compile ns))
