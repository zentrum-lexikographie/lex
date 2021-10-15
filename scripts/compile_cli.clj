(binding [*compile-path* "classes/cli"]
  (doseq [ns ['clojure.tools.logging.impl 'zdl.lex.cli]]
    (compile ns)))
(shutdown-agents)
