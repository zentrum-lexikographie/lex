(ns zdl.lex.client.view.export
  (:require [byte-streams :as bs]
            [clojure.java.io :as io]
            [manifold.deferred :as d]
            [seesaw.chooser :as chooser]
            [seesaw.core :as ui]
            [seesaw.forms :as forms]
            [zdl.lex.client :as client]
            [zdl.lex.client.auth :as auth])
  (:import java.io.File))

(defn csv-ext?
  [^File f]
  (.. f (getName) (toLowerCase) (endsWith ".csv")))

(defn ^File choose-csv-file
  [parent]
  (when-let [^File f (chooser/choose-file parent
                                          :type :save
                                          :filters [["CSV" ["csv"]]]
                                          :all-files? false)]
    (if (csv-ext? f) f (io/file (str (.. f (getPath)) ".csv")))))

(defn create-progress-dialog
  [parent ^File f {:keys [total]}]
  (ui/dialog :title "Ergebnisse exportieren"
             :parent parent
             :content (forms/forms-panel
                       "center:150dlu"
                       :items
                       [(ui/label :text (format "%d Ergebnis(se)" total))
                        (ui/progress-bar :indeterminate? true)
                        (ui/label :text (str f))])
             :options []))

(defn response-body->file
  [{:keys [body]} ^File f]
  (io/copy (bs/to-input-stream body) f))

(defn open-dialog
  ([results]
   (open-dialog results nil))
  ([{:keys [query total] :as results} parent]
   (let [parent (some-> parent ui/to-root)]
     (when-let [csv-file (choose-csv-file parent)]
       (let [progress-dialog (create-progress-dialog parent csv-file results)]
         (->
          (auth/with-authentication
            (client/export-article-metadata query :limit 50000))
          (d/chain #(response-body->file % csv-file))
          (d/catch #(ui/alert (.getMessage %)))
          (d/finally #(ui/dispose! progress-dialog)))
         (-> progress-dialog (ui/pack!) (ui/show!) (ui/invoke-soon)))))))
