(ns zdl-lex-wikimedia.dump
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip])
  (:import javax.xml.stream.XMLInputFactory
           javax.xml.transform.stream.StreamSource))

(def ^:private namespace-filter
  "Administrative page namespaces"
  (complement
   #{
     "Benutzer"
     "Benutzer Diskussion"
     "Datei"
     "Datei Diskussion"
     "Diskussion"
     "Flexion"
     "Hilfe"
     "Hilfe Diskussion"
     "Kategorie"
     "Kategorie Diskussion"
     "MediaWiki"
     "MediaWiki Diskussion"
     "Medium"
     "Spezial"
     "Thesaurus"
     "Thesaurus Diskussion"
     "Verzeichnis"
     "Verzeichnis Diskussion"
     "Vorlage"
     "Vorlage Diskussion"
     "Wiktionary"
     "Wiktionary Diskussion"}))

(def ^:private regex-filter
  "Regex-based page filter"
  (complement
   (some-fn (partial re-seq #"^Archiv ")
            (partial re-seq #"^Liste ")
            (partial re-seq #" \(Konjugation\)$")
            (partial re-seq #" \(Deklination\)$"))))

(defn- content-page? [{:keys [title]}]
  "Filters pages by title, removing administrative pages"
  (let [[ns ln] (str/split title #":")]
    (or (nil? ln) (and (namespace-filter ns) (regex-filter title)))))

(defn- page-property [node & path]
  (as-> node $
    (zip/xml-zip $)
    (apply zx/xml1-> $ path)
    (zx/xml-> $ dz/descendants zip/node string?)
    (apply str $)))

(xml/alias-uri :mw "http://www.mediawiki.org/xml/export-0.10/")

(def pages
  (comp
    (partial filter content-page?)
    (partial map #(array-map :title (page-property % ::mw/title)
                             :timestamp (page-property % ::mw/revision ::mw/timestamp)
                             :content (page-property % ::mw/revision ::mw/text)))
    (partial filter (comp #{::mw/page} :tag))
    :content
    xml/parse
    io/input-stream))
