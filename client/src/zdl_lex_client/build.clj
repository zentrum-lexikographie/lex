(ns zdl-lex-client.build
  (:require [me.raynes.fs :as fs]
            [uberdeps.api :as uberdeps]
            [zdl-lex-common.log :as log]))

(def compiled-ns
  ['zdl-lex-client.oxygen.extension
   'zdl-lex-client.oxygen.plugin
   'zdl-lex-client.oxygen.url-handler])

(def jar-path
  "../oxygen/plugin/lib/org.zdl.lex.client-standalone.jar")

(defn -main [& args]
  (log/configure)
  (binding [*compile-path* "classes"
            *compiler-options* {:disable-locals-clearing true
                                :elide-meta []
                                :direct-linking false}]
    (fs/delete-dir *compile-path*)
    (fs/mkdirs *compile-path*)
    (doseq [ns compiled-ns] (compile ns)))
  (let [deps (-> "deps.edn" slurp read-string)]
    (uberdeps/package deps jar-path {:aliases #{:prod}})))

(comment (-main))
