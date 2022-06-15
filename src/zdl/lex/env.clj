(ns zdl.lex.env
  (:require [aero.core :as aero]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io])
  (:import io.github.cdimascio.dotenv.Dotenv))

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
    (or (System/getenv k) (.get dot-env k))))

(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(def config
  (aero/read-config (io/resource "zdl/lex/config.edn")))

(defn in-namespaces?
  [k & nss]
  (some #(str/starts-with? (namespace k) %) nss))

(defn in-server-namespace?
  [k]
  (in-namespaces? k "zdl.lex.server"))

(def config-keys
  (into (sorted-set)
        (filter qualified-keyword?)
        (keys config)))

(def server-config-keys
  (into (sorted-set) (filter in-server-namespace?) config-keys))

(def client-config-keys
  (into (sorted-set) (remove in-server-namespace?) config-keys))


