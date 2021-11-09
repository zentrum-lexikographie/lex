(binding [*compile-path*     "classes/client"
          *compiler-options* {:disable-locals-clearing true
                              :elide-meta              []
                              :direct-linking          false}]
  (doseq [ns ['clojure.tools.logging.impl
              'zdl.lex.client.io
              'zdl.lex.client.oxygen
              'zdl.lex.client.plugin]]
    (compile ns)))
(shutdown-agents)
