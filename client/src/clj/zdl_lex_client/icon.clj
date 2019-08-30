(ns zdl-lex-client.icon
  (:import [java.awt Color Font]
           [jiconfont.swing IconFontSwing]
           [jiconfont.icons.google_material_design_icons GoogleMaterialDesignIcons])
  (:require [seesaw.core :as ui]
            [seesaw.font :as font]))

(IconFontSwing/register (.. GoogleMaterialDesignIcons getIconFont))

(defn icon [name] (IconFontSwing/buildIcon name 20))

(def gmd-add (icon GoogleMaterialDesignIcons/ADD))
(def gmd-all (icon GoogleMaterialDesignIcons/STAR))
(def gmd-bug-report (icon GoogleMaterialDesignIcons/BUG_REPORT))
(def gmd-delete (icon GoogleMaterialDesignIcons/DELETE))
(def gmd-details (icon GoogleMaterialDesignIcons/DETAILS))
(def gmd-export (icon GoogleMaterialDesignIcons/SAVE))
(def gmd-filter (icon GoogleMaterialDesignIcons/FILTER_LIST))
(def gmd-help (icon GoogleMaterialDesignIcons/HELP))
(def gmd-search (icon GoogleMaterialDesignIcons/SEARCH))
(def gmd-refresh (icon GoogleMaterialDesignIcons/REFRESH))
(def gmd-result (icon GoogleMaterialDesignIcons/LIST))
(def gmd-web (icon GoogleMaterialDesignIcons/WEB))

(def logo (ui/label :icon "zdl.png" :border 6 :size [32 :by 32]))

(comment
  (ui/invoke-later
   (-> (ui/frame :title "Test" :content (ui/button :icon gmd-delete :text "Hallo"))
       ui/pack!
       ui/show!)))
