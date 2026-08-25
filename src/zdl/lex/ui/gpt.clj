(ns zdl.lex.ui.gpt
  (:require
   [seesaw.core :as ui]
   [zdl.lex.ui.util :refer [icon]]
   [seesaw.behave :as behave]
   [clojure.string :as str]
   [seesaw.bind :as uib]
   [zdl.lex.client :as client]
   [zdl.lex.markdown :as md]
   [zdl.lex.util :refer [norm-str]])
  (:import
   (java.awt Color)
   (java.awt.event KeyEvent)
   (javax.swing JEditorPane)
   (ro.sync.exml.workspace.api.standalone.ui ToolbarButton)))

(def persona
  (doto (ui/text
         :border "Persona:"
         :text (@client/gpt-chat :persona)
         :multi-line? true
         :wrap-lines? true
         :rows 2)
    (behave/when-focused-select-all)))

(defn gpt-message->html
  [{role "role" content "content"}]
  (format "<div class=\"%s\">%s</div>" role (md/render content)))

(defn render-gpt-chat-history
  [history]
  (or (->> (map gpt-message->html history) (str/join) (norm-str))
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
    (.setText (render-gpt-chat-history (@client/gpt-chat :history)))))

(def dialog-pane
  (doto (ui/scrollable dialog :hscroll :never)
    (.. (getViewport) (setBackground Color/WHITE))))

(defn scroll-dialog-to-end!
  [& _]
  (ui/invoke-later
   (let [scrollbar (.getVerticalScrollBar dialog-pane)]
     (.setValue scrollbar (.getMaximum scrollbar)))))


(def prompt
  (doto (ui/text :border "Nächste Anweisung:"
                 :wrap-lines? true
                 :multi-line? true
                 :rows 2)
    (behave/when-focused-select-all)))


(defn rollback-history
  [& _]
  (let [{:keys [history]} @client/gpt-chat
        interactions      (partition-all 2 history)
        history           (vec (flatten (butlast interactions)))
        prompt-text       (-> interactions last first (get "content"))]
    (send client/gpt-chat assoc :history history :prompt nil)
    (ui/invoke-later (ui/text! prompt prompt-text) (.requestFocus prompt))))

(defn clear-history
  [& _]
  (send client/gpt-chat assoc :history []))

(defn prompt-gpt
  [& _]
  (when-let [prompt (norm-str (ui/text prompt))]
    (send-off client/gpt-chat
              client/gpt-request->socket
              prompt
              (norm-str (ui/text persona)))))

(def clear-action
  (ui/action :name "Löschen"
             :icon (icon :delete)
             :mnemonic \l
             :handler clear-history))

(def rollback-action
  (ui/action :name "Zurück"
             :icon (icon :arrow-back)
             :mnemonic \b
             :handler rollback-history))

(def prompt-action
  (ui/action :name "Senden"
             :icon (icon :arrow-forward)
             :mnemonic \s
             :handler prompt-gpt))

(defn on-prompt-input
  [^KeyEvent e]
  (when (.isControlDown e)
    (condp = (.getKeyCode e)
      KeyEvent/VK_ENTER (prompt-gpt)
      KeyEvent/VK_LEFT  (rollback-history)
      nil)))

(ui/listen prompt :key-pressed on-prompt-input)

(def prompt-progress
  (ui/progress-bar :indeterminate? false))

(uib/bind
 client/gpt-chat
 (uib/tee
  (uib/bind (uib/transform :persona)
            (uib/property persona :text))
  (uib/bind (uib/transform (comp render-gpt-chat-history :history))
            (uib/tee
             (uib/property dialog :text)
             (uib/b-do* scroll-dialog-to-end!)))
  (uib/bind (uib/transform :prompt)
            (uib/tee
             (uib/property prompt :text)
             (uib/bind (uib/transform nil?)
                       (uib/tee
                        (uib/property persona :enabled?)
                        (uib/property prompt :enabled?)))
             (uib/bind (uib/transform some?)
                       (uib/property prompt-progress :indeterminate?))))))

(def chat-panel
  (ui/border-panel
   :border 5
   :north (ui/border-panel
           :hgap 5
           :north (ui/scrollable persona :hscroll :never)
           :center (ui/toolbar
                    :floatable? false
                    :orientation :horizontal
                    :items [(ToolbarButton. clear-action false)
                            (ToolbarButton. rollback-action false)
                            (ToolbarButton. prompt-action false)
                            (javax.swing.Box/createHorizontalGlue)
                            prompt-progress]))
   :center dialog-pane
   :south (ui/scrollable prompt :hscroll :never)))

(def login-panel
  (ui/border-panel))

(def panel
  (ui/card-panel :items [[login-panel :login] [chat-panel :chat]]))

(uib/bind
 client/active-user
 (uib/b-do* #(ui/show-card! panel (if % :chat :login))))
