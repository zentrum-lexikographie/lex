(binding [*compile-path* "classes/server"]
  (doseq [ns ['clojure.tools.logging.impl 'zdl.lex.server]]
    (compile ns)))
(shutdown-agents)
