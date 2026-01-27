(ns zdl.lex.ui.gpt
  (:require [seesaw.core :as ui]
            [zdl.lex.ui.util :refer [icon]]
            [seesaw.behave :as behave]
            [clojure.string :as str]
            [seesaw.bind :as uib]
            [zdl.lex.client :as client])
  (:import
   (java.awt Color)
   (java.awt.event KeyEvent)
   (javax.swing JEditorPane)
   (ro.sync.exml.workspace.api.standalone.ui ToolbarButton)))

(def persona
  (doto (ui/text
         :border "Persona:"
         :text "Du bist ein Lexikograph und gibst kurze, genaue Antworten!"
         :multi-line? true
         :wrap-lines? true
         :rows 2)
    (behave/when-focused-select-all)))

(defn message->html
  [{role "role" content "content"}]
  (format "<div class=\"%s\">%s</div>" role content))

(defn messages->html
  [messages]
  (or (not-empty (str/join (map message->html messages)))
      (str "<div class=\"note\"><p>Bitte geben Sie eine Anweisung ein und "
           "bestätigen Sie die Eingabe mit <i>Strg+Enter</i>.</p></div>")))

(def dialog-editor-kit
  (JEditorPane/createEditorKitForContentType "text/html"))

(def dialog-styles
  ["p {margin-top: 0; margin-bottom: 5px}"
   ".user, .assistant, .note {padding: 5px; margin: 5px}"
   ".assistant {border-left: 1px solid #e7e7e7}"
   ".user {border-left: 1px solid #002bff; color: #002bff}"
   ".note {color: #666666}"])

(doseq [css dialog-styles]
  (..  dialog-editor-kit (getStyleSheet) (addRule css)))

(def dialog
  (doto (ui/styled-text :editable? false
                        :wrap-lines? true
                        :background :white
                        :margin 5)
    (.setEditorKit dialog-editor-kit)
    (.setText (messages->html @client/gpt-chat))))

(def dialog-pane
  (doto (ui/scrollable dialog :hscroll :never)
    (.. (getViewport) (setBackground Color/WHITE))))

(defn scroll-dialog-to-end!
  [& _]
  (ui/invoke-later
   (let [scrollbar (.getVerticalScrollBar dialog-pane)]
     (.setValue scrollbar (.getMaximum scrollbar)))))

(uib/bind
 client/gpt-chat
 (uib/tee
  (uib/bind (uib/transform messages->html) (uib/notify-later)
            (uib/property dialog :text))
  (uib/b-do* scroll-dialog-to-end!)))

(declare prompt)

(defn is-ctrl-enter?
  [^KeyEvent e]
  (and (.isControlDown e) (= KeyEvent/VK_ENTER (.getKeyCode e))))

(def prompt
  (doto (ui/text :border "Nächste Anweisung:"
                 :wrap-lines? true
                 :multi-line? true
                 :rows 2)
    (behave/when-focused-select-all)))

(defn clear-prompt!
  [& _]
  (ui/text! prompt ""))

(defn focus-prompt!
  [& _]
  (ui/invoke-later (.requestFocus prompt)))

(def rollback-action
  (ui/action :name "Zurück"
             :icon (icon :arrow-back)
             :mnemonic \b
             :handler (fn [_])))

(def clear-action
  (ui/action :name "Löschen"
             :icon (icon :delete)
             :mnemonic \l
             :handler (fn [_]
                        (client/clear-gpt-chat)
                        (clear-prompt!)
                        (focus-prompt!))))

(defn send-dialog
  [& _]
  (when-let [s (not-empty (str/trim (ui/text prompt)))]
    (clear-prompt!)
    (client/append-to-gpt-chat {"role" "user" "content" s})))

(def send-action
  (ui/action :name "Senden"
             :icon (icon :arrow-forward)
             :mnemonic \s
             :handler send-dialog))

(ui/listen prompt
           :key-pressed (fn [e] (when (is-ctrl-enter? e) (send-dialog))))

(def action-pane
  (ui/flow-panel :items [(ui/button :action send-action)
                         (ui/button :action clear-action)]
                 :align :center))

(def toolbar
  (ui/toolbar :floatable? false
              :orientation :horizontal
              :items [(ToolbarButton. rollback-action false)
                      (ToolbarButton. send-action false)
                      (ToolbarButton. clear-action false)
                      (javax.swing.Box/createHorizontalGlue)
                      (ui/progress-bar :indeterminate? false)]))

(def panel
  (ui/border-panel
   :border 5
   :north (ui/border-panel :hgap 5 :north persona :center toolbar)
   :center dialog-pane
   :south prompt))
