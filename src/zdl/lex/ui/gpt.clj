(ns zdl.lex.ui.gpt
  (:require [seesaw.core :as ui]
            [zdl.lex.ui.util :refer [clip-to-screen-size icon]]
            [seesaw.behave :as behave]
            [clojure.string :as str]
            [seesaw.bind :as uib])
  (:import
   (java.awt Color)
   (java.awt.event KeyEvent)))

(def persona
  (doto (ui/text
         :text "Du bist ein Lexikograph und gibst kurze, genaue Antworten!"
         :multi-line? true
         :rows 2
         :border "Persona:")
    (behave/when-focused-select-all)))

(def messages
  (atom ["Das ist ein Test?" "Ja, das ist ein Test."]))

(defn messages->html
  [messages]
  (str/join (map #(str "<div><p>" % "</p></div>") messages)))

(def dialog
  (doto (ui/styled-text :editable? false
                        :wrap-lines? true
                        :background :white
                        :margin 10)
    (.setContentType "text/html")
    (.setText (messages->html @messages))))

(uib/bind
 messages
 (uib/tee
  (uib/bind (uib/transform messages->html)
            (uib/property dialog :text))
  (uib/b-do*
   (fn [_]
     (ui/invoke-later
       (.setCaretPosition dialog (.. dialog (getDocument) (getLength))))))))

(def dialog-pane
  (doto (ui/scrollable dialog :hscroll :never)
    (.. (getViewport) (setBackground Color/WHITE))))

(uib/subscribe
 messages
 (fn [_]
   (ui/invoke-later
     (let [scrollbar (.getVerticalScrollBar dialog-pane)]
       (.setValue scrollbar (.getMaximum scrollbar))))))

(def role->color
  {"user"      "#e7e7e7"
   "assistant" "#002bff"})

(declare prompt)

(defn is-ctrl-enter?
  [^KeyEvent e]
  (and (.isControlDown e) (= KeyEvent/VK_ENTER (.getKeyCode e))))

(declare send-dialog)

(def prompt
  (doto (ui/text :border "Nächste Anweisung:"
                 :wrap-lines? true
                 :multi-line? true
                 :rows 2)
    (behave/when-focused-select-all)
    (ui/listen :key-pressed (fn [e] (when (is-ctrl-enter? e) (send-dialog))))))

(defn clear-prompt!
  [& _]
  (ui/text! prompt ""))

(defn focus-prompt!
  [& _]
  (ui/invoke-later (.requestFocus prompt)))

(def clear-action
  (ui/action :name "Löschen"
             :icon (icon :clear-all)
             :mnemonic \l
             :handler (fn [_]
                        (reset! messages [])
                        (clear-prompt!)
                        (focus-prompt!))))

(defn send-dialog
  [& _]
  (when-let [s (not-empty (str/trim (ui/text prompt)))]
    (clear-prompt!)
    (swap! messages conj s)))

(def send-action
  (ui/action :name "Senden"
             :icon (icon :send)
             :mnemonic \s
             :handler send-dialog))

(def action-pane
  (ui/flow-panel :items [(ui/button :action send-action)
                         (ui/button :action clear-action)]
                 :align :center))
(def frame
  (ui/frame
   :title   "ZDL Lex – Sprachmodell-Dialog"
   :size    (clip-to-screen-size [800 :by 600])
   :content (ui/border-panel
             :border 5
             :north persona
             :center dialog-pane
             :south (ui/vertical-panel :items [prompt action-pane]))))

(comment
  (do (-> frame ui/show! ui/invoke-now) (focus-prompt!)))
