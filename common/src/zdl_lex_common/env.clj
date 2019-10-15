(ns zdl-lex-common.env
  (:require [clojure.spec.alpha :as s]
            [environ.core :as environ]
            [spec-tools.core :as st]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [me.raynes.fs :as fs]))

(def defaults
  {:log-level :info
   :repl-port 3001
   :http-port 3000
   :http-log false
   :http-anon-user "nobody"
   :data-dir "../data"
   :server-base "https://lex.dwds.de/"
   :exist-base "http://spock.dwds.de:8080/exist"
   :mantis-base "http://odo.dwds.de/mantis"
   :mantis-project 5
   :mantis-password "test"
   :solr-base "http://localhost:8983/solr/"
   :solr-core "articles"})

(s/def ::log-level
  (st/spec
   {:spec timbre/valid-level?
    :type :keyword
    :decode/string #(-> %2 name str/lower-case keyword)
    :encode/string #(-> %2 name str/upper-case)}))

(s/def ::data-dir
  (st/spec
   {:spec fs/directory?
    :type :file
    :decode/string #(-> %2 fs/file fs/absolute fs/normalized)
    :encode/string #(.. %2 (getAbsolutePath))}))

(s/def ::repl-port int?)

(s/def ::http-port int?)
(s/def ::http-log boolean?)
(s/def ::http-anon-user string?)

(s/def ::server-base string?)
(s/def ::server-user string?)
(s/def ::server-password string?)

(s/def ::exist-base string?)
(s/def ::exist-user string?)
(s/def ::exist-password string?)

(s/def ::mantis-base string?)
(s/def ::mantis-project int?)
(s/def ::mantis-user string?)
(s/def ::mantis-password string?)

(s/def ::solr-base string?)
(s/def ::solr-core string?)

(s/def ::corpora-user string?)
(s/def ::corpora-password string?)

(s/def ::env
  (s/keys :req-un [::log-level ::repl-port
                   ::http-port ::http-log ::http-anon-user
                   ::data-dir
                   ::server-base
                   ::exist-base
                   ::mantis-base ::mantis-project
                   ::solr-base ::solr-core]
          :opt-un [::server-user ::server-password
                   ::exist-user ::exist-password
                   ::mantis-user ::mantis-password
                   ::corpora-user ::corpora-password]))

(defn secret? [k]
  (let [n (name k)]
    (some #(str/includes? n %) ["password" "passwd" "secret" "key" "ps1"])))

(defn env->str [env]
  (let [key-width (reduce max (map (comp count name) (keys env)))
        kv-format (str "%-" (+ key-width 2) "s = %s")]
    (->>
     [["" (apply str (map (constantly "-") (range 78)))]
      (for [[k v] env :let [v (if (secret? k) "***" v)]]
        (format kv-format k v))
      [(apply str (map (constantly "-") (range 78)))]]
     (mapcat identity)
     (str/join \newline))))

(defn coerce [m]
  (st/coerce ::env m st/string-transformer))

(let [coerce #(st/coerce ::env % st/string-transformer)
      normalize-key #(-> % name (str/replace #"^zdl-lex-" "") keyword)
      config (->> (conj (fs/parents ".") (fs/file "."))
                  (map #(fs/file % "zdl-lex-config.edn"))
                  (filter fs/file?)
                  (map (comp read-string slurp))
                  (apply merge))]
  (def env
    (->>
     (map #(vector (-> % first normalize-key) (second %)) (merge environ/env config))
     (into defaults)
     (coerce) (s/assert* ::env)
     (into (sorted-map)))))

(comment
  (timbre/info (env->str env)))
