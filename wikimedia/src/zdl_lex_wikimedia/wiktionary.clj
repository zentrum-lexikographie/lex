(ns zdl-lex-wikimedia.wiktionary
  (:require [clojure.string :as str]
            [zdl-lex-wikimedia.dump :as dump]))

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

(defn article? [{:keys [title]}]
  "Filters pages by title, removing administrative pages"
  (and title
       (let [[ns ln] (str/split title #":")]
         (or (nil? ln) (and (namespace-filter ns) (regex-filter title))))))

(defmacro with-current-wiktionary
  [& body]
  `(dump/with-revisions ["dewiktionary" "pages-meta-current" "latest" "xml" "bz2"]
     ~@body))

(defmacro with-wiktionary-history
  [& body]
  `(dump/with-revisions ["dewiktionary" "pages-meta-history"]
     ~@body))
