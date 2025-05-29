(ns zdl.lex.ui.qa
  (:require
   [clojure.string :as str]
   [seesaw.bind :as uib]
   [seesaw.core :as ui]
   [zdl.lex.client :as client]
   [zdl.lex.oxygen.workspace :as workspace]
   [zdl.lex.ui.util :as util])
  (:import
   (ro.sync.document DocumentPositionedInfo)
   (ro.sync.exml.workspace.api.results ResultsManager ResultsManager$ResultType)))

(def active?
  (atom false))

(def action
  (ui/action :name "Typographieprüfung"
             :tip "Typographieprüfung (deaktiviert)"
             :icon (util/icon :error-outline)
             :handler (fn [_] (swap! active? not))))

(let [active-icon   (util/icon :error)
      inactive-icon (util/icon :error-outline)]
  (uib/bind
   active?
   (uib/tee
    (uib/bind
     (uib/transform #(if % active-icon inactive-icon))
     (uib/property action :icon))
    (uib/bind
     (uib/transform #(str "Typographieprüfung (" (when-not % "de") "aktiviert)"))
     (uib/property action :tip)))))

(def tab-key
  "ZDL - Typographie")

(def result-type
  ResultsManager$ResultType/PROBLEM)

(defn set-errors!
  ([manager]
   (set-errors! manager nil))
  ([^ResultsManager manager dpis]
   (. manager (setResults tab-key dpis result-type))))

(def error-type->desc
  {:invalid-chars         "Zeichenfehler"
   :unbalanced-parens     "Paarzeichenfehler"
   :missing-whitespace    "Fehlender Weißraum"
   :redundant-whitespace  "Unnötiger Weißraum"
   :unknown-abbreviations "Nicht erlaubte Abkürzung"
   :final-punctuation     "Interpunktion am Belegende prüfen"})

(defn error->message
  [{:keys [ctx type data]}]
  (str/join
   " – "
   [(str "<" (name (get ctx :tag)) "/>")
    (get error-type->desc type type)
    (str/join ", " (map #(str "/" % "/") data))]))

(defn error->dpi
  [url {{{:keys [line column]} :location} :ctx :as error}]
  (DocumentPositionedInfo. DocumentPositionedInfo/SEVERITY_WARN
                           (error->message error) url line column))

(defn article->dpis
  [id article]
  (let [url (str (client/id->url id))]
    (into [] (map (partial error->dpi url) (article ::client/qa)))))

(uib/subscribe
 (uib/funnel active? client/active-article client/articles)
 (fn [_]
   (when workspace/instance
     (ui/invoke-now
      (let [manager (.getResultsManager workspace/instance)]
        (if-not @active?
          (set-errors! manager)
          (let [id @client/active-article]
            (if-let [article (@client/articles id)]
              (set-errors! manager (article->dpis id article))
              (set-errors! manager)))))))))
