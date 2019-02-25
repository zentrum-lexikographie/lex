(ns dwdsox.db
  (:require [dwdsox.env :refer [config]]
            [dwdsox.basex :as basex]
            [dwdsox.exist-db :as exist]))

(defn- db-resolve [sym]
  (var-get (condp = (get-in config [:env :db-type])
    :basex (ns-resolve 'dwdsox.basex sym)
    :exist (ns-resolve 'dwdsox.exist-db sym))))

(def collection (db-resolve 'collection))

(def query (db-resolve 'query))

(def whoami (db-resolve 'whoami))
