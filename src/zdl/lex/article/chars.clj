(ns zdl.lex.article.chars
  (:require [clojure.string :as str])
  (:import [java.text Normalizer Normalizer$Form]))

(def latin-chars
  ;; note: there's no decomposition for ø
  "abcdefghijklmnopqrstuvwxyzßABCDEFGHIJKLMNOPQRSTUVWXYZÆæĐđŁłŒœØø€£ðʒ")

(def greek-chars
  "λθξνόησιϛεϊδωο")

(def arabic-figures
  "0123456789")

(def diacritics
  "\u0300\u0301\u0302\u0303\u0304\u0306\u0308\u030a\u030b\u030c\u0327\u0328")

(def whitespace
  "space, newline, zero width space, zero width joiner"
  " \n\u200b\u200d")

(def punctuation
  "bare minimum for //Definition et al. (note: RIGHT SINGLE QUOTATION MARK!)"
  ".,()’-»«")

(def punctuation-extended
  "slightly extended for text like data (note: SOLIDUS vs. FRACTION SLASH!)"
  ":;?!/⁄–›‹%‰&=§°+†*@¬×·$€⊆±")

(def punctuation-extra
  "even more extended for //Fundstelle"
  "[]_~")

(def grammar-symbols
  "-_/")

(defn invalid-chars-fn
  [& sets]
  (let [cs (into #{} (apply str sets))]
    (fn [s]
      (when-let [data (some->>
                       (Normalizer/normalize s Normalizer$Form/NFKD)
                       (remove cs) (distinct) (seq) (vec))]
        {:type ::invalid :data data}))))

(def check-all
  (invalid-chars-fn
   whitespace latin-chars greek-chars arabic-figures diacritics
   punctuation punctuation-extended punctuation-extra))

(def check-phrase
  (invalid-chars-fn
   whitespace arabic-figures latin-chars diacritics punctuation))

(def check-text
  (invalid-chars-fn
   whitespace latin-chars greek-chars arabic-figures diacritics
   punctuation punctuation-extended))

(def check-grammar
  (invalid-chars-fn
   whitespace latin-chars arabic-figures diacritics punctuation
   grammar-symbols))

(def parenthesis-exceptions
  #"(?:\s[a-c]\))|(?:\u200b:-?[()])")

(def non-parenthesis
  #"[^»«\(\)\[\]]")

(defn remove-matching-parentheses
  [s]
  (loop [before s]
    (let [after (str/replace before #"(?:»«)|(?:\(\))|(?:\[\])" "")]
      (if (= (count before) (count after))
        (not-empty before)
        (recur after)))))

(defn check-parentheses
  [s]
  (when-let [data (some->
                   s
                   (str/replace parenthesis-exceptions "")
                   (str/replace non-parenthesis "")
                   (remove-matching-parentheses))]
    {:type ::unbalanced-parens :data data}))
