(ns zdl.lex.server.consistency
  (:require [clojure.string :as str]
            [zdl.lex.article :as article]
            [zdl-xml.util :as xml]
            [zdl.lex.server.git :as git]))

(let [numerals {\I 1, \V 5, \X 10, \L 50, \C 100, \D 500, \M 1000}
      add-numeral (fn [n t] (if (> n (* 4 t)) (- n t) (+ t n)))]
  (defn roman
    "Converts a roman to decimal numbers.
     http://steveliles.github.io/roman_numeral_conversion_in_clojure.html"
    [s]
    (reduce add-numeral (map numerals (reverse s)))))

(def ref-levels
  [#"[IVXLCDM]+" ;; roman numerals
   #"[A-Z]" ;; upper-case latin letters
   #"[1-9]" ;; arabic numbers
   #"[a-z]" ;; lower-case latin letters
   #"[αβγδεζηθικλ]" ;; lower-case greek letters
   ])

(defn parse-sense-ref [s]
  (-> (str/replace s #"u\." " ")
      (str/replace #"und" " ")
      (str/replace #"\s+" " ")
      (str/trim)
      (str/split #"\s")))

;; [A-Z] [IVX]
(comment
  (roman "MMMCIX")
  (for [article (->> (article/article-xml-files git/dir) (take 100))
        ref (->> article xml/->xdm article/references)
        :let [{:keys [sense lemma]} ref]
        :when sense]
    [article ref (parse-sense-ref sense)]))

