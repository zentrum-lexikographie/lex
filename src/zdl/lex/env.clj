(ns zdl.lex.env
  (:require
   [aero.core :as aero]
   [camel-snake-kebab.core :as csk]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [integrant.core :as ig])
  (:import
   (io.github.cdimascio.dotenv Dotenv)))

(def ^Dotenv dot-env
  "Load `.env` (also into system properties)"
  (.. Dotenv (configure) (ignoreIfMissing) (systemProperties) (load)))

(defn getenv
  ([k]
   (getenv k nil))
  ([k df]
   (let [k (str "ZDL_LEX_" (csk/->SCREAMING_SNAKE_CASE_STRING k))]
     (or (some->> k System/getProperty str/trim not-empty)
         (some->> k System/getenv str/trim not-empty)
         df))))

(defmethod aero/reader 'dotenv
  [_ _ value]
  (let [k (csk/->SCREAMING_SNAKE_CASE_STRING value)]
    (or (some->> k System/getenv str/trim not-empty)
        (some->> k (.get dot-env) str/trim not-empty))))

(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(def config
  (aero/read-config (io/resource "zdl/lex/config.edn")))

(defn in-namespaces?
  [k & nss]
  (some #(str/starts-with? (namespace k) %) nss))

(defn in-dev-namespace?
  [k]
  (in-namespaces? k "zdl.lex.client.dev"))

(defn in-server-namespace?
  [k]
  (in-namespaces? k "zdl.lex.server"))

(def config-keys
  (into (sorted-set)
        (filter qualified-keyword?)
        (keys config)))

(def server-config-keys
  (into (sorted-set)
        (comp
         (filter in-server-namespace?)
         (remove in-dev-namespace?))
        config-keys))

(def client-config-keys
  (into (sorted-set)
        (comp
         (remove in-server-namespace?)
         (remove in-dev-namespace?))
        config-keys))

(def dev-config-keys
  (into (sorted-set)
        (remove #(in-namespaces? % "zdl.lex.client.repl"))
        config-keys))
