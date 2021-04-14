(ns zdl.lex.build.docker
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clojure.string :as str]
            [me.raynes.conch :refer [with-programs]]))

(defn parse-json
  [s]
  (json/parse-string s csk/->kebab-case-keyword))

(def parse-json-lines
  (comp (partial map parse-json) str/split-lines))

(defn networks
  []
  (with-programs [docker]
    (->> (docker "network" "ls" "--format" "{{json .}}")
         (parse-json-lines))))

(defn network-by-name
  [name]
  (->> (networks)
       (filter (comp #{name} :name))
       (first)))

(def zdl-lex-network-name
  "zdl_lex")

(defn create-network!
  []
  (when-not (network-by-name zdl-lex-network-name)
    (with-programs [docker]
      (docker "network" "create" zdl-lex-network-name))))

(defn processes
  []
  (with-programs [docker]
    (->> (docker "ps" "-a" "--format" "{{json .}}")
         (parse-json-lines))))

(defn process-by-name
  [name]
  (when-let [process (->> (processes)
                          (filter (comp #{name} :names))
                          (first))]
    (assoc process :running? (->> process :status (re-seq #"^Up")))))

(def solr-process-name
  "zdl_lex_solr")

(defn start-solr!
  [{:keys [image] :or {image "docker.zdl.org/zdl-lex/solr:latest"}}]
  (let [{:keys [running?] :as process} (process-by-name solr-process-name)]
    (when-not running?
      (create-network!)
      (with-programs [docker]
        (if process
          (docker "start" solr-process-name)
          (docker "run"
                  "--name" solr-process-name
                  "--network" zdl-lex-network-name
                  "-p" "8983:8983"
                  "-d" image)))))
  (System/exit 0))

(def server-process-name
  "zdl_lex_server")

(defn start-server!
  [{:keys [image] :or {image "docker.zdl.org/zdl-lex/server:latest"}}]
  (with-programs [docker]
    (when-let [process (process-by-name server-process-name)]
      (docker "rm" "-f" server-process-name))
    (create-network!)
    (docker "run"
            "--name" server-process-name
            "--network" zdl-lex-network-name
            "-e" "ZDL_LEX_SOLR_URL=http://zdl_lex_solr:8983/solr/"
            "-p" "3000:3000"
            "-d" image))
  (System/exit 0))

