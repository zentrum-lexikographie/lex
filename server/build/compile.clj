(ns compile)

(def compiled-ns ['zdl-lex-server.core])

(defn -main [& args]
  (binding [*compile-path* "classes"]
    (doseq [ns compiled-ns] (compile ns))))
