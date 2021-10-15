(binding [*compile-path*     "classes/client"
          *compiler-options* {:disable-locals-clearing true
                              :elide-meta              []
                              :direct-linking          false}]
  (doseq [ns ['clojure.tools.logging.impl
              'zdl.lex.client.oxygen.extension
              'zdl.lex.client.oxygen.plugin
              'zdl.lex.client.oxygen.url-handler]]
    (compile ns)))
(shutdown-agents)
