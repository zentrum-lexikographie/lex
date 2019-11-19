(ns package
  (:require [uberdeps.api :as uberdeps]))

(def jar-path
  "../docker/validator/org.zdl.lex.validator.jar")

(defn -main [& args]
  (let [deps (-> "deps.edn" slurp read-string)]
    (uberdeps/package deps jar-path {:aliases #{:prod}})))
