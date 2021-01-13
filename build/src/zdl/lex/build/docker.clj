(ns zdl.lex.build.docker
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clojure.string :as str]
            [me.raynes.conch :refer [with-programs]]))

(defn parse-json
  [s]
  (json/parse-string s csk/->kebab-case-keyword))

(defn processes
  []
  (with-programs [docker]
    (->> (docker "ps" "-a" "--format" "{{json .}}")
         (str/split-lines)
         (map parse-json))))

(def solr-process-name
  "zdl_lex_solr")

(defn solr-process
  []
  (when-let [process (->> (processes)
                          (filter (comp #{solr-process-name} :names))
                          (first))]
    (assoc process :running? (->> process :status (re-seq #"^Up")))))

(defn start-solr!
  [{:keys [image] :or {image "lex.dwds.de/zdl-lex/solr:latest"}}]
  (let [{:keys [running?] :as process} (solr-process)]
    (when-not running?
      (with-programs [docker]
        (if process
          (docker "start" solr-process-name)
          (docker "run" "--name" solr-process-name
                  "-p" "8983:8983" "-d" image)))))
  (println (solr-process))
  (System/exit 0))

(comment
  (solr-process))
