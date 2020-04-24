(ns zdl.lex.client.oxygen.editor-variables
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [zdl.lex.client.bus :as bus]))

(def system-user
  (System/getProperty "user.name"))

(def current-user
  (atom nil))

(defstate current-user-update
  :start (uib/bind (bus/bind [:status])
                   (uib/transform (comp :user second))
                   current-user)
  :stop (current-user-update))

(def descriptions
  [{:name "${zdl.user}"
    :desc "Anmeldename des aktuellen ZDL-Lex-Benutzers"}])

(defn resolve-vars
  [^String editor-url ^String s]
  (some-> s
          (str/replace #"\$\{zdl\.user\}"
                       (or @current-user system-user ""))))
