(def compiled-ns
  ['clojure.tools.logging.impl
   'zdl-lex-client.oxygen.extension
   'zdl-lex-client.oxygen.plugin
   'zdl-lex-client.oxygen.url-handler])

(binding [*compiler-options* {:disable-locals-clearing true
                              :elide-meta []
                              :direct-linking false}]
  (doseq [ns compiled-ns] (compile ns)))
