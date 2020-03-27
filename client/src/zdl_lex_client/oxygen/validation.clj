(ns zdl-lex-client.oxygen.validation
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as ws]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.typography.chars :as typo-chars]
            [zdl-lex-common.typography.token :as typo-token])
  (:import net.sf.saxon.s9api.XdmNode
           ro.sync.document.DocumentPositionedInfo
           ro.sync.exml.workspace.api.PluginWorkspace
           [ro.sync.exml.workspace.api.results ResultsManager ResultsManager$ResultType]))

(def active? (atom false))

(def tab-key
  "ZDL - Typographie")

(def error-type->desc
  {::typo-chars/invalid "Zeichenfehler"
   ::typo-chars/unbalanced-parens "Paarzeichenfehler"
   ::typo-token/missing-whitespace "Fehlender Weißraum"
   ::typo-token/redundant-whitespace "Unnötiger Weißraum"
   ::typo-token/unknown-abbreviations "Nicht erlaubte Abkürzung"
   ::typo-token/final-punctuation "Interpunktion am Belegende prüfen"})

(defn error->message
  [{:keys [^XdmNode ctx type data]}]
  (str/join
   " – "
   [(str "<" (.. ctx (getNodeName) (getLocalName)) "/>")
    (get error-type->desc type type)
    (str/join ", " (map #(str "/" % "/") data))]))

(defn error->dpi
  [url {:keys [^XdmNode ctx] :as error}]
  (let [line-number (.getLineNumber ctx)
        column-number (.getColumnNumber ctx)]
    (DocumentPositionedInfo.
     DocumentPositionedInfo/SEVERITY_WARN
     (error->message error)
     (str url) line-number column-number)))

(defn with-results-manager
  [f]
  (when (instance? PluginWorkspace ws/instance)
    (f (. ^PluginWorkspace ws/instance (getResultsManager)))))

(defn dpi-matches-system-id?
  [system-id ^DocumentPositionedInfo dpi]
  (= system-id (.getSystemID dpi)))

(defn remove-results!
  [manager url]
  (let [all-results (. manager (getAllResults tab-key))
        matches-url? (partial dpi-matches-system-id? (str url))]
    (doseq [dpi (filter matches-url? all-results)]
      (. manager (removeResult tab-key dpi)))))

(def result-type
  ResultsManager$ResultType/PROBLEM)

(defn reset-results!
  ([manager] (reset-results! manager nil))
  ([manager dpis]
   (. manager (setResults tab-key dpis result-type)))
  ([manager url dpis]
   (remove-results! manager url)
   (when (seq dpis)
     (. manager (addResults tab-key dpis result-type true)))))

(defn add-results
  [url]
  (with-results-manager
    (fn [manager]
      (some->>
       (ws/xml-document ws/instance url)
       (article/doc->articles)
       (mapcat article/check-typography)
       (map (partial error->dpi url))
       (vec) (reset-results! manager url)))))

(defn clear-results
  [url]
  (with-results-manager
    (fn [manager]
      (reset-results! manager url nil))))

(defn handle-activation
  [active?]
  (with-results-manager
    (fn [manager]
      (reset-results! manager)
      (when active?
        (doseq [url (ws/editor-urls ws/instance)]
          (add-results url))))))

(def activation-action
  (ui/action :name "Typographieprüfung"
             :tip "Typographieprüfung (deaktiviert)"
             :icon icon/gmd-error-outline
             :handler (fn [_] (swap! active? not))))

(defstate activation-states
  :start
  [(uib/bind
    active?
    (uib/transform #(str "Typographieprüfung (" (if-not % "de") "aktiviert)"))
    (uib/property activation-action :tip))
   (uib/bind
    active?
    (uib/transform #(if %  icon/gmd-error icon/gmd-error-outline))
    (uib/property activation-action :icon))
   (uib/subscribe
    active?
    handle-activation)]
  :stop (doseq [unsubscribe! activation-states] (unsubscribe!)))

(defn update-validation
  [topic url]
  (when @active?
    (cond
      (#{:editor-opened :editor-saved} topic) (add-results url)
      (#{:editor-closed} topic) (clear-results url))))

(defstate validation-updates
  :start (bus/listen [:editor-opened :editor-saved :editor-closed]
                     update-validation)
  :stop (validation-updates))
