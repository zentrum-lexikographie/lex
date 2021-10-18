(ns zdl.lex.client.export
  (:require [clojure.java.io :as io]
            [seesaw.chooser :as chooser]
            [seesaw.core :as ui]
            [seesaw.forms :as forms]
            [zdl.lex.client.http :as client.http]
            [clojure.core.async :as a])
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

(defn open-dialog
  ([results]
   (open-dialog results nil))
  ([{:keys [query] :as results} parent]
   (let [parent (some-> parent ui/to-root)]
     (when-let [csv-file (choose-csv-file parent)]
       (let [progress-dialog (create-progress-dialog parent csv-file results)]
         (a/thread
           (try
             (let [request  {:method       :get
                             :url          "index/export"
                             :as           :input-stream
                             :query-params {:q     query
                                            :limit 50000}}
                   response (client.http/request request)]
               (io/copy (get response :body) csv-file))
             (catch Throwable t
               (ui/alert (.getMessage t)))
             (finally
               (ui/dispose! progress-dialog))))
         (-> progress-dialog (ui/pack!) (ui/show!) (ui/invoke-soon)))))))
