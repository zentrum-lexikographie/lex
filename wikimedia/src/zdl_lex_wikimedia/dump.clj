(ns zdl-lex-wikimedia.dump
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [zdl-lex-common.util :refer [->clean-map]]))

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

(defn- content-page? [{:keys [title content]}]
  "Filters pages by title, removing administrative pages"
  (and content
       title
       (let [[ns ln] (str/split title #":")]
         (or (nil? ln) (and (namespace-filter ns) (regex-filter title))))))

(xml/alias-uri :mw "http://www.mediawiki.org/xml/export-0.10/")

(defn- page-property [loc & path]
  (->> (concat path [dz/descendants zip/node string?])
       (apply zx/xml-> loc)
       (apply str)
       (not-empty)))

(defn- page->map [page]
  (let [property (partial page-property (zip/xml-zip page))]
    (->clean-map
     {:title (property ::mw/title)
      :content (property ::mw/revision ::mw/text)
      :timestamp (property ::mw/revision ::mw/timestamp)
      :author (property ::mw/revision ::mw/contributor ::mw/username)
      :comment (property ::mw/revision ::mw/comment)})))

(defn pages [source]
  "Produces a seq of page data from a parsed XML source"
  (->> (io/input-stream source)
       (xml/parse)
       (:content)
       (filter (comp #{::mw/page} :tag))
       (map page->map)
       (filter content-page?)))
