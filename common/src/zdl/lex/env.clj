(ns zdl.lex.env
  (:require [clojure.string :as str]
            [mount.core :as mount])
  (:import io.github.cdimascio.dotenv.Dotenv))

(def ^:private host-name
  (.. java.net.InetAddress getLocalHost getHostName))

(def ^Dotenv dot-env
  (.. (Dotenv/configure) (ignoreIfMissing) (load)))

(defn getenv
  ([k]
   (getenv k nil nil))
  ([k ek]
   (getenv k ek nil))
  ([k ek dv]
   (or (some->> k (get (mount/args)))
       (some->> ek (.get dot-env) str/trim not-empty)
       dv)))
