(ns zdl.lex.oxygen.workspace
  (:require
   [clojure.string :as str]
   [lambdaisland.uri :as uri]
   [seesaw.core :as ui]
   [taoensso.telemere :as tm]
   [zdl.lex.client :as client])
  (:import
   (java.net URL)
   (ro.sync.exml.workspace.api PluginWorkspace)
   (ro.sync.exml.workspace.api.listeners WSEditorChangeListener WSEditorListener)
   (ro.sync.exml.workspace.api.standalone StandalonePluginWorkspace)))

(def ^{:tag StandalonePluginWorkspace :dynamic true} instance
  nil)

(def editing-area
  PluginWorkspace/MAIN_EDITING_AREA)

(defn open-editor-xml
  [^URL url]
  (.. instance (getEditorAccess url editing-area) (createContentInputStream)))

(defn update-article
  [url id]
  (tm/with-ctx+ {::url url}
    (client/update-article id #(open-editor-xml url))))

(defn editor-listener
  "An editor listener for a given resource location"
  [url id]
  (proxy [WSEditorListener] []
    (documentTypeExtensionsReconfigured [])
    (editorPageAboutToBeChangedVeto [_] true)
    (editorPageChanged [])
    (editorAboutToBeClosedVeto [_] true)
    (editorAboutToBeSavedVeto [_] true)
    (editorSaved [_] (update-article url id))))

(def editor-listeners
  (atom {}))

(defn add-editor!
  [url id]
  (let [listener (editor-listener url id)]
    (ui/invoke-now
     (.. instance
         (getEditorAccess url editing-area)
         (addEditorListener listener))
     (swap! editor-listeners assoc id listener))))

(defn remove-editor!
  [url id]
  (ui/invoke-now
   (.. instance
       (getEditorAccess url editing-area)
       (removeEditorListener (@editor-listeners id)))
   (swap! editor-listeners dissoc id)))

(defn remove-all-editors!
  []
  (doseq [[id _listener] @editor-listeners]
    (let [url (URL. (str (client/id->url id)))]
      (remove-editor! url id)
      (client/dissoc-article id))))

(def editor-change-listener
  (proxy [WSEditorChangeListener] []
    (editorAboutToBeOpened [_])
    (editorAboutToBeOpenedVeto [_] true)
    (editorOpened [url]
      (when-let [id (client/url->id url)]
        (add-editor! url id)
        (update-article url id)))
    (editorPageChanged [_])
    (editorRelocated [from to]
      (when-let [id (client/url->id from)]
        (remove-editor! from id)
        (client/dissoc-article id))
      (when-let [id (client/url->id to)]
        (add-editor! to id)))
    (editorAboutToBeClosed [url]
      (when-let [id (client/url->id url)]
        (remove-editor! url id)
        (client/dissoc-article id))
      true)
    (editorsAboutToBeClosed [urls]
      (doseq [url urls]
        (when-let [id (client/url->id url)]
          (remove-editor! url id)
          (client/dissoc-article id)))
      true)
    (editorClosed [url])
    (editorActivated [url]
      (when-let [id (client/url->id url)]
        (reset! client/active-article id)))
    (editorSelected [_])
    (editorDeactivated [_])))

(defn bind-editor-change-listener
  []
  (ui/invoke-now
   (remove-all-editors!)
   (.addEditorChangeListener instance editor-change-listener editing-area)))

(defn unbind-editor-change-listener
  []
  (ui/invoke-now
   (.removeEditorChangeListener instance editor-change-listener editing-area)
   (remove-all-editors!)))

(def views
  {:results "zdl-lex-results-view"
   :toolbar "zdl-lex-client-toolbar"
   :issue   "zdl-lex-issue-view"
   :links   "zdl-lex-links-view"})

(defn show-view
  [view]
  (when instance
    (.showView instance (views view) false)))

(defn open-url
  [url]
  (when instance
    (.openInExternalApplication instance (URL. (str url)) false "text/html")))

(defn open-article
  [id]
  (when instance
    (.open instance (URL. (str (client/id->url id))))))

(def preview-url
  (uri/uri "https://zwei.dwds.de/wb/existdb/"))

(defn preview-article
  [& _]
  (when instance
    (let [editor-access (.getCurrentEditorAccess instance editing-area)]
      (when-let [url (.getEditorLocation editor-access)]
        (when-let [id (client/url->id url)]
          (if (.isModified editor-access)
            (->>
             ["Der aktuelle Artikel ist nicht gespeichert."
              (str "Bitte speichern Sie ihre Arbeit, "
                   "um eine aktuelle Vorschau zu erhalten.")]
             (str/join \newline)
             (ui/dialog :parent (.getParentFrame instance)
                        :modal? true
                        :type :error
                        :content)
             (ui/pack!)
             (ui/show!)
             (ui/invoke-later))
            (open-url (uri/assoc-query preview-url :d id))))))))
