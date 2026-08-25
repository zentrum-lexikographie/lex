(ns zdl.lex.dev
  (:require
   [babashka.fs :as fs]
   [clojure.java.process :as p]
   [com.potetm.fusebox.retry :as retry :refer [with-retry delay-exp]]
   [integrant.core :as ig]
   [integrant.repl :as ig.repl]
   [nextjournal.clerk :as clerk]
   [pg.core :as pg]
   [seesaw.bind :as uib]
   [seesaw.core :as ui]
   [taoensso.telemere :as tel]
   [zdl.lex.article :as article]
   [zdl.lex.client :as client]
   [zdl.lex.db :as db]
   [zdl.lex.git :as git]
   [zdl.lex.gpt]
   [zdl.lex.index :as index]
   [zdl.lex.oxygen.url-handler :as url-handler]
   [zdl.lex.server :as server]
   [zdl.lex.ui.issue :as issue]
   [zdl.lex.ui.links :as links]
   [zdl.lex.ui.search :as search]
   [zdl.lex.ui.toolbar :as toolbar]
   [zdl.lex.ui.util :as util]
   [zdl.lex.ui.gpt :as gpt]))

(def backend-start-retry
  (retry/init
   {::retry/retry? (fn [n _ms _ex] (< n 20))
    ::retry/delay  (fn [n _ms _ex] (min (delay-exp 100 n) 1000))}))

(defn backend-running?
  []
  (not-empty (p/exec "docker" "compose" "ps" "-q")))

(defn start-backend!
  []
  (when-not (backend-running?)
    (p/exec "docker" "compose" "--progress" "quiet" "up" "-d"
            "db" "index" "queue")
    (tel/with-min-level nil "com.potetm.fusebox.retry" :warn
      (with-retry backend-start-retry
        (index/query {"q" "id:*" "rows" "0" "wt" "json"})
        (pg/with-connection [c db/spec] (pg/query c "select 1+1 as n"))))
    (backend-running?)))

(defn stop-backend!
  []
  (p/exec "docker" "compose"  "--progress" "quiet" "down"
          "db" "index" "queue"))

(defn backend-fixture
  [f]
  (try
    (start-backend!)
    (f)
    (finally
      (stop-backend!))))

(defn init-test-data
  []
  (git/init!)
  (when-let [existing (seq (git/xml-paths))]
    (run! git/rm! existing)
    (git/commit!))
  (fs/copy-tree (fs/file "test" "data") git/*dir*)
  (run! git/add! (git/xml-paths))
  (git/commit!))

(defn test-data-fixture
  [f]
  (init-test-data)
  (f))

(defn index-fixture
  [f]
  (try
    (index/sync-articles!)
    (f)
    (finally
      (index/clear!))))

(url-handler/install-stream-handler!)

(def editor
  (ui/text :border 5
           :font {:from :monospaced :style :bold :size (util/large-font-size)}
           :foreground "#999"
           :multi-line? true
           :editable? false))


(def article-panel
  (ui/border-panel
   :north toolbar/widget
   :center (ui/splitter :left-right
                        (ui/splitter :top-bottom
                                     search/panel
                                     (ui/scrollable editor)
                                     :divider-location 0.75
                                     :resize-weight 0.75)
                        (ui/splitter :top-bottom
                                     (ui/splitter
                                      :top-bottom
                                      (ui/scrollable links/pane :hscroll :never)
                                      issue/panel
                                      :divider-location 0.5
                                      :resize-weight 0.5)
                                     gpt/panel
                                     :divider-location 0.4
                                     :resize-weight 0.4)
                        :divider-location 0.75
                        :resize-weight 0.75)))


(def frame
  (ui/frame
   :title   "ZDL Lex – Dev"
   :size    (util/clip-to-screen-size)
   :content article-panel #_(ui/scrollable examples/table)))

(defmethod ig/init-key ::search->article
  [_ _]
  (uib/subscribe
   search/opened-article-id
   (fn [id]
     (client/dissoc-article @client/active-article)
     (client/update-article id #(client/http-get-article id))
     (reset! client/active-article id))))

(defmethod ig/halt-key! ::search->article
  [_ subscription]
  (subscription))

(defmethod ig/init-key ::article->editor
  [_ _]
  (uib/bind
   (uib/funnel client/active-article client/articles)
   (uib/transform
    (fn [_]
      (let [active-article @client/active-article
            articles       @client/articles]
        (some-> active-article articles ::client/xml article/->str))))
   (uib/value editor)))

(defmethod ig/halt-key! ::article->editor
  [_ subscription]
  (subscription))

(defmethod ig/init-key ::console
  [_ _]
  (ui/invoke-now (ui/show! frame))
  frame)

(defmethod ig/halt-key! ::console
  [_ frame]
  (ui/dispose! frame))

(def ui-config
  {::search->article {}
   ::article->editor {}
   ::console         {}})

(integrant.repl/set-prep!
 (constantly
  (merge client/config
         server/config
         zdl.lex.gpt/dev-config
         ui-config)))

(defn go
  []
  (start-backend!)
  (init-test-data)
  (index/sync-articles!)
  (ig.repl/go))

(defn halt
  []
  (ig.repl/halt))

(defn notebooks!
  []
  (clerk/serve! {:browse? true :watch-paths ["notebooks"]}))
