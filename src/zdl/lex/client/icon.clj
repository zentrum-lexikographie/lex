(ns zdl.lex.client.icon
  (:require [seesaw.core :as ui])
  (:import jiconfont.icons.google_material_design_icons.GoogleMaterialDesignIcons
           jiconfont.swing.IconFontSwing))

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
(def gmd-error (icon GoogleMaterialDesignIcons/ERROR))
(def gmd-error-outline (icon GoogleMaterialDesignIcons/ERROR_OUTLINE))
(def gmd-refresh (icon GoogleMaterialDesignIcons/REFRESH))
(def gmd-result (icon GoogleMaterialDesignIcons/LIST))
(def gmd-web (icon GoogleMaterialDesignIcons/WEB))
(def gmd-link-incoming (icon GoogleMaterialDesignIcons/ARROW_BACK))
(def gmd-link-outgoing (icon GoogleMaterialDesignIcons/ARROW_FORWARD))
(def gmd-link-bidi (icon GoogleMaterialDesignIcons/COMPARE_ARROWS))

(def logo (ui/label :icon "zdl.png" :border 6 :size [32 :by 32]))

(comment
  (->
   (ui/frame
    :title "Test"
    :content (ui/toggle :selected? false :icon gmd-spellcheck
                        :text "Typographieprüfung"))
   ui/pack!
   ui/show!
   ui/invoke-later))
