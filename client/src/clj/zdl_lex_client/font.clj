(ns zdl-lex-client.font
  (:require [seesaw.font :refer [default-font to-font]]))

(defn derived [& args]
  (to-font (merge {:from (default-font "Label.font")}
                  (apply hash-map args))))
