(ns zdl.lex.client.util
  (:require [seesaw.util :refer [to-dimension]])
  (:import
   (java.awt Toolkit)))

(defn dim->vec
  [d]
  (let [d (to-dimension d)]
    [(.getWidth d) (.getHeight d)]))

(defn screen-size
  []
  (to-dimension (. (Toolkit/getDefaultToolkit) (getScreenSize))))

(defn clip-to-screen-size
  ([]
   (clip-to-screen-size (screen-size)))
  ([d & {:keys [inset] :or {inset 25}}]
   (let [[dim-width dim-height]       (dim->vec d)
         [screen-width screen-height] (dim->vec (screen-size))]
     (to-dimension [(min dim-width (- screen-width inset))
                    :by
                    (min dim-height (- screen-height inset))]))))
