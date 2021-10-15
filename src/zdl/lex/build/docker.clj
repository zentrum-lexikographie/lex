(ns zdl.lex.build.docker
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [jsonista.core :as json]
            [clojure.string :as str]
            [zdl.lex.sh :refer [sh!]]
            [zdl.lex.util :refer [exec!]]))

(defn parse-json
  [s]
  (cske/transform-keys csk/->kebab-case-keyword (json/read-value s)))

(def parse-json-lines
  (comp (partial map parse-json) str/split-lines))

(defn networks
  []
  (->> (sh! "docker" "network" "ls" "--format" "{{json .}}")
       :out (parse-json-lines)))

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
    (sh! "docker" "network" "create" zdl-lex-network-name)))

(defn processes
  []
  (->> (sh! "docker" "ps" "-a" "--format" "{{json .}}")
       :out (parse-json-lines)))

(defn process-by-name
  [name]
  (when-let [process (->> (processes)
                          (filter (comp #{name} :names))
                          (first))]
    (assoc process :running? (->> process :status (re-seq #"^Up")))))

(def solr-process-name
  "zdl_lex_solr")

(defn start-solr
  [{:keys [image] :or {image "docker-registry.zdl.org/zdl-lex/solr:latest"}}]
  (let [{:keys [running?] :as process} (process-by-name solr-process-name)]
    (when-not running?
      (create-network!)
      (if process
        (sh! "docker" "start" solr-process-name)
        (sh! "docker" "run"
             "--name" solr-process-name
             "--network" zdl-lex-network-name
             "--network-alias" "solr"
             "-p" "8983:8983"
             "-d" image)))))

(def start-solr!
  (partial exec! start-solr))

(def server-process-name
  "zdl_lex_server")

(defn start-server
  [{:keys [image] :or {image "docker-registry.zdl.org/zdl-lex/server:latest"}}]
  (when (process-by-name server-process-name)
    (sh! "docker" "rm" "-f" server-process-name))
  (create-network!)
  (sh! "docker" "run"
       "--name" server-process-name
       "--network" zdl-lex-network-name
       "-e" "ZDL_LEX_SOLR_URL=http://solr:8983/solr/"
       "-p" "3000:3000"
       "-d" image))

(def start-server!
  (partial exec! start-server))
