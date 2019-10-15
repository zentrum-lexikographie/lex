(ns zdl-lex-server.build
  (:require [me.raynes.fs :as fs]
            [uberdeps.api :as uberdeps]
            [zdl-lex-common.log :as log]))

(def jar-path
  "../ansible/files/api/org.zdl.lex.server-standalone.jar")

(defn -main [& args]
  (log/configure)
  (let [deps (-> "deps.edn" slurp read-string)]
    (uberdeps/package deps jar-path {:aliases #{:prod}})))

(comment (-main))


