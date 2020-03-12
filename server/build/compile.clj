(ns compile)

(def compiled-ns
  ['clojure.tools.logging.impl
   'zdl-lex-server.core])

(defn -main [& args]
  (binding [*compile-path* "classes"]
    (doseq [ns compiled-ns] (compile ns))))
