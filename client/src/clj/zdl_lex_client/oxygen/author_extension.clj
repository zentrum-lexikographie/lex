(ns zdl-lex-client.oxygen.author-extension
  (:require [taoensso.timbre :as timbre]
            [clojure.string :as str])
  (:import [ro.sync.ecss.extensions.api
            AuthorExtensionStateListener AuthorSchemaAwareEditingHandlerAdapter]
           [ro.sync.contentcompletion.xml SchemaManagerFilter])
  (:gen-class
   :name de.zdl.oxygen.AuthorExtension
   :extends ro.sync.ecss.extensions.api.ExtensionsBundle))

(defonce author-access (atom {}))

(defn with-author-access [bundle f]
  (some-> (@author-access bundle) f))

(defn with-some-author-access [f]
  (with-author-access (-> @author-access keys first) f))

(defn -getDescription [this]
  "A costum extension bundle extended by a context-sensitive grey-out of the lexicography menu and a manipulation of the content completion")

(defn -getDocumentTypeID [this]
  "DWDS.Framework.document.type")

(defn -createAuthorExtensionStateListener [bundle]
  (proxy [AuthorExtensionStateListener] []
    (activated [bundle-author-access]
      (swap! author-access assoc bundle bundle-author-access))
    (deactivated [_]
      (swap! author-access dissoc bundle))))

(defn action-based-element-filter [author-access]
  (let [action-names (.. author-access
                         getEditorAccess
                         getActionsProvider
                         getAuthorExtensionActions
                         keySet)
        action-tokens (mapcat #(str/split % #"\s+") action-names)
        action-tokens (apply hash-set action-tokens)]
    (fn [el]
      (action-tokens (.getQName el)))))

(defn -createSchemaManagerFilter [bundle]
  (let [element-filter (with-author-access bundle action-based-element-filter)]
    (proxy [SchemaManagerFilter] []
      (filterAttributes [attrs ctx]
        attrs)
      (filterAttributeValues [values ctx]
        values)
      (filterElements [elements ctx]
        (timbre/info {:elements elements})
        ;;(vec (filter element-filter elements)))
        elements)
      (filterElementValues [values ctx]
        values))))

(defn -getAuthorSchemaAwareEditingHandler [bundle]
  (proxy [AuthorSchemaAwareEditingHandlerAdapter] []
    ;; TODO
    ))
