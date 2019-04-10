(ns zdl-lex-client.extension-bundle
  (:import [ro.sync.ecss.extensions.api
            AuthorExtensionStateListener AuthorSchemaAwareEditingHandlerAdapter]
           [ro.sync.contentcompletion.xml SchemaManagerFilter])
  (:gen-class
   :name de.zdl.oxygen.ExtensionBundle
   :extends ro.sync.ecss.extensions.api.ExtensionsBundle
   :state state
   :init init))

(defn -init []
  [[] (ref {:author-access (atom nil)})])

(defn -getDescription [this]
  "A costum extension bundle extended by a context-sensitive grey-out of the lexicography menu and a manipulation of the content completion")

(defn -getDocumentTypeID [this]
  "DWDS.Framework.document.type")

(defn -createAuthorExtensionStateListener [bundle]
  (proxy [AuthorExtensionStateListener] []
    (activated [author-access])
      ;;(reset! (-> (.state bundle) deref :author-access) author-access))
    (deactivated [_])))
      ;;(reset! (-> (.state bundle) deref :author-access) nil))))

(defn -createSchemaManagerFilter [bundle]
  (proxy [SchemaManagerFilter] []
    (filterAttributes [attrs ctx]
      attrs)
    (filterAttributeValues [values ctx]
      values)
    (filterElements [elements ctx]
      elements)
    (filterElementValues [values ctx]
      values)))

(defn -getAuthorSchemaAwareEditingHandler [bundle]
  (proxy [AuthorSchemaAwareEditingHandlerAdapter] []
    ;; TODO
    ))
