(ns package
  (:require [uberdeps.api :as uberdeps]))

(def jar-path
  "../oxygen/plugin/lib/org.zdl.lex.client-standalone.jar")

(defn -main [& args]
  (let [deps (-> "deps.edn" slurp read-string)]
    (uberdeps/package deps jar-path {:aliases #{:prod}})))


