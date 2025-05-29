(ns zdl.lex.ui.util
  (:require
   [camel-snake-kebab.core :as csk]
   [seesaw.core :as ui]
   [seesaw.font]
   [seesaw.util :refer [to-dimension]])
  (:import
   (java.awt Toolkit)
   (jiconfont.icons.google_material_design_icons GoogleMaterialDesignIcons)
   (jiconfont.swing IconFontSwing)))

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

(defn default-font []
  (seesaw.font/default-font "Label.font"))

(defn default-font-size []
  (.. (default-font) (getSize)))

(defn large-font-size []
  (* 1.25 (default-font-size)))

(defn derived-font
  [& {:as args}]
  (seesaw.font/to-font (merge {:from (default-font)} args)))

;; cf. http://jiconfont.github.io/googlematerialdesignicons

(IconFontSwing/register (.. GoogleMaterialDesignIcons getIconFont))

(defn icon
  ([k]
   (icon k 20))
  ([k size]
   (as-> k $
     (csk/->SCREAMING_SNAKE_CASE_STRING $)
     (. (.getDeclaredField GoogleMaterialDesignIcons $) (get nil))
     (IconFontSwing/buildIcon $ size))))

(defn do-on-selection
  [f]
  (fn [e]
    (when-not (.getValueIsAdjusting e)
      (when-let [selected (ui/selection e)]
        (ui/selection! e nil)
        (f selected)))))
