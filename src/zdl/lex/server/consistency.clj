(ns zdl.lex.server.consistency
  (:require [clojure.string :as str]
            [zdl.lex.article :as article]))

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
  (for [{:keys [anchors] :as article} (take 10 (article/articles "../../zdl-wb/WDG"))
        {:keys [anchor sense]} (get article :links)
        :when sense]
    [anchors anchor (parse-sense-ref sense)]))

