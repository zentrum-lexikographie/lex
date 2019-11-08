(ns zdl-lex-common.env
  (:require [clojure.spec.alpha :as s]
            [environ.core :as environ]
            [spec-tools.core :as st]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [me.raynes.fs :as fs]))

(def ^:private host-name
  (.. java.net.InetAddress getLocalHost getHostName))

(def defaults
  {:log-level :info
   :repl-port 3001
   :http-port 3000
   :http-log false
   :http-anon-user "nobody"
   :data-dir "../data"
   :git-auth-user "lex"
   :git-auth-password "lex"
   :git-origin "git@lex.dwds.de:lex.git"
   :git-branch (str "zdl-lex-server/" host-name)
   :git-commit-user "ZDL-Lex"
   :git-commit-email "noreply@lex.dwds.de"
   :server-base "https://lex.dwds.de/"
   :exist-base "http://spock.dwds.de:8080/exist"
   :mantis-base "http://odo.dwds.de/mantis"
   :mantis-project 5
   :mantis-user "test"
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
   {:spec some?
    :type :file
    :decode/string #(-> %2 fs/file fs/absolute fs/normalized)
    :encode/string #(.. %2 (getAbsolutePath))}))

(s/def ::repl-port int?)

(s/def ::http-port int?)
(s/def ::http-log boolean?)
(s/def ::http-anon-user string?)

(s/def ::git-auth-user string?)
(s/def ::git-auth-password string?) 
(s/def ::git-origin string?)
(s/def ::git-branch string?)
(s/def ::git-commit-user string?)
(s/def ::git-commit-email string?)

(s/def ::server-base string?)
(s/def ::server-user string?)
(s/def ::server-password string?)

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
                   ::git-auth-user ::git-auth-password
                   ::git-origin ::git-branch
                   ::git-commit-user ::git-commit-email
                   ::server-base
                   ::mantis-base ::mantis-project
                   ::solr-base ::solr-core]
          :opt-un [::server-user ::server-password
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
        (format kv-format k (pr-str v)))
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
     (map #(vector (-> % first normalize-key) (second %)) (merge config environ/env))
     (into defaults)
     (coerce) (s/assert* ::env)
     (into (sorted-map)))))

(comment
  (timbre/info (env->str env)))
