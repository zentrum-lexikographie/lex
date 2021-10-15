(ns zdl.lex.client.oxygen.editor-variables
  (:require [clojure.string :as str]
            [zdl.lex.client.status :as status]))

(def system-user
  (System/getProperty "user.name"))

(def descriptions
  [{:name "${zdl.user}"
    :desc "Anmeldename des aktuellen ZDL-Lex-Benutzers"}])

(defn resolve-vars
  [^String editor-url ^String s]
  (when s
    (str/replace s #"\$\{zdl\.user\}" (or @status/current-user system-user ""))))
