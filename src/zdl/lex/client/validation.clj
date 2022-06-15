(ns zdl.lex.client.validation
  (:require [gremid.data.xml :as dx]
            [clojure.string :as str]
            [integrant.core :as ig]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl.lex.bus :as bus]
            [zdl.lex.client.icon :as client.icon]
            [zdl.lex.client.editors :as client.editors]
            [zdl.lex.article.chars :as chars]
            [zdl.lex.article.token :as tokens]
            [zdl.lex.article.validate :as av])
  (:import ro.sync.document.DocumentPositionedInfo
           (ro.sync.exml.workspace.api.standalone StandalonePluginWorkspace)
           [ro.sync.exml.workspace.api.results ResultsManager$ResultType]))

(def active?
  (atom false))

(def action
  (ui/action :name "Typographieprüfung"
             :tip "Typographieprüfung (deaktiviert)"
             :icon client.icon/gmd-error-outline
             :handler (fn [_]
                        (let [validate? (swap! active? not)]
                          (bus/publish! #{:validate?} {:validate? validate?})))))

(def tab-key
  "ZDL - Typographie")

(def error-type->desc
  {::chars/invalid "Zeichenfehler"
   ::chars/unbalanced-parens "Paarzeichenfehler"
   ::tokens/missing-whitespace "Fehlender Weißraum"
   ::tokens/redundant-whitespace "Unnötiger Weißraum"
   ::tokens/unknown-abbreviations "Nicht erlaubte Abkürzung"
   ::tokens/final-punctuation "Interpunktion am Belegende prüfen"})

(defn error->message
  [{:keys [ctx type data]}]
  (str/join
   " – "
   [(str "<" (name (get ctx :tag)) "/>")
    (get error-type->desc type type)
    (str/join ", " (map #(str "/" % "/") data))]))

(defn error->dpi
  [url {:keys [ctx] :as error}]
  (let [{{:keys [line-number column-number]} ::dx/location-info} (meta ctx)]
    (DocumentPositionedInfo.
     DocumentPositionedInfo/SEVERITY_WARN
     (error->message error)
     (str url) line-number column-number)))

(defn dpi-matches-system-id?
  [system-id ^DocumentPositionedInfo dpi]
  (= system-id (.getSystemID dpi)))

(defn remove-results!
  [manager url]
  (ui/invoke-now
   (let [all-results (. manager (getAllResults tab-key))
         matches-url? (partial dpi-matches-system-id? (str url))]
     (doseq [dpi (filter matches-url? all-results)]
       (. manager (removeResult tab-key dpi))))))

(def result-type
  ResultsManager$ResultType/PROBLEM)

(defn reset-results!
  ([manager]
   (reset-results! manager nil))
  ([manager dpis]
   (ui/invoke-later
    (. manager (setResults tab-key dpis result-type))))
  ([manager url dpis]
   (ui/invoke-later
    (remove-results! manager url)
    (when (seq dpis)
      (. manager (addResults tab-key dpis result-type true))))))

(defn add-results!
  [manager url doc]
  (let [errors (av/check-typography doc)
        dpis   (vec (map #(error->dpi url %) errors))]
    (reset-results! manager url dpis)))

(defn clear-results!
  [manager url]
  (reset-results! manager url nil))

(defn validate!
  [^StandalonePluginWorkspace workspace topic {:keys [validate? url doc]}]
  (let [results-manager (.getResultsManager workspace)]
    (when (= :validate? topic)
      (reset-results! results-manager)
      (when validate?
        (doseq [[url doc] (client.editors/read-editor-contents workspace)]
          (add-results! results-manager url doc))))
    (when (= :editor-closed topic)
      (clear-results! results-manager url))
    (when (and @active?
               (#{:editor-opened :editor-activated :editor-saved} topic))
      (add-results! results-manager url doc))))

(def validation-events
  #{:validate? :editor-opened :editor-closed :editor-activated :editor-saved})

(defn bind-validation-events!
  [workspace]
  (bus/listen validation-events (partial validate! workspace)))

(defmethod ig/init-key ::events
  [_ _]
  [(uib/bind
    active?
    (uib/transform #(if % client.icon/gmd-error client.icon/gmd-error-outline))
    (uib/property action :icon))
   (uib/bind
    active?
    (uib/transform #(str "Typographieprüfung (" (when-not % "de") "aktiviert)"))
    (uib/property action :tip))])

(defmethod ig/halt-key! ::events
  [_ callbacks]
  (doseq [callback callbacks] (callback)))
