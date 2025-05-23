(ns zdl.lex.client.font
  (:require [seesaw.font :refer [default-font to-font]]))

(defn default []
  (default-font "Label.font"))

(defn default-size []
  (.. (default) (getSize)))

(defn large-size []
  (* 1.25 (default-size)))

(defn derived [& args]
  (to-font (merge {:from (default)}
                  (apply hash-map args))))
