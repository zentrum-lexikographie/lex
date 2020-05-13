(def compiled-ns
  ['clojure.tools.logging.impl
   'zdl.lex.cli])

(doseq [ns compiled-ns] (compile ns))
