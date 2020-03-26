(ns zdl-lex-client.oxygen.validation
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as ws]
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
    (str/join ", " (map #(str "„…" % "…”") data))]))

(defn error->dpi
  [url {:keys [^XdmNode ctx] :as error}]
  (let [line-number (.getLineNumber ctx)
        column-number (.getColumnNumber ctx)]
    (DocumentPositionedInfo.
     DocumentPositionedInfo/SEVERITY_WARN
     (error->message error)
     (str url) line-number column-number)))

(defn report-errors?
  []
  (and @active? (instance? PluginWorkspace ws/instance)))

(defn clear-results
  [url]
  (if (report-errors?)
    (let [manager (. ^PluginWorkspace ws/instance (getResultsManager))
          results (. manager (getAllResults tab-key))
          system-id (str url)
          url-matches? (fn [^DocumentPositionedInfo dpi]
                         (= system-id (.getSystemID dpi)))]
      (doseq [dpi (filter url-matches? results)]
        (. manager (removeResult tab-key dpi))))
    (log/infof "- %s" url)))

(defn add-results
  [{:keys [url errors status] :as article}]
  (if (report-errors?)
    (do
      (clear-results url)
      (when errors
        (.. ^PluginWorkspace ws/instance
            (getResultsManager)
            (setResults
             tab-key
             (vec (map (partial error->dpi url) errors))
             ResultsManager$ResultType/PROBLEM))))
    (log/info ws/instance)))

(defn handle-results
  [topic payload]
  (when (report-errors?)
    (condp = topic
      :article (add-results payload)
      :editor-closed (clear-results payload))))

(defn handle-activation
  [active?])

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

(defstate validation-results
  :start (bus/listen [:article :editor-closed] handle-results)
  :stop (validation-results))
