(def compiled-ns
  ['clojure.tools.logging.impl
   'zdl.lex.server.core])

(doseq [ns compiled-ns] (compile ns))
