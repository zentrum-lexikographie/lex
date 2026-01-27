(ns zdl.lex.markdown
  (:import
   (com.vladsch.flexmark.html HtmlRenderer)
   (com.vladsch.flexmark.parser Parser)))

(def parser
  (.. (Parser/builder) (build)))

(def renderer
  (.. (HtmlRenderer/builder) (build)))

(defn render
  [s]
  (.render renderer (.parse parser s)))
