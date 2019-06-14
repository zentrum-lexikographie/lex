(ns zdl-lex-client.details
  (:require [seesaw.core :as ui]
            [zdl-lex-client.editors :as editors]
            [seesaw.bind :as uib]
            [zdl-lex-client.article :as article]))

(def ^:private active (ui/label :text "-"))

(def panel (ui/scrollable
            (ui/border-panel :center active)))

(uib/bind
 editors/active
 (uib/transform #(some-> % article/url->id))
 (uib/transform #(or % "-"))
 (uib/property active :text))
