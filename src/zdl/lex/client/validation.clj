(ns zdl.lex.client.validation
  (:require [clojure.data.xml :as dx]
            [clojure.string :as str]
            [seesaw.core :as ui]
            [zdl.lex.article.chars :as chars]
            [zdl.lex.article.token :as tokens]
            [zdl.lex.article.validate :as av])
  (:import ro.sync.document.DocumentPositionedInfo
           [ro.sync.exml.workspace.api.results ResultsManager$ResultType]))

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
