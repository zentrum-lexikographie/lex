(ns zdl-lex-client.view.export
  (:require [clojure.java.io :as io]
            [seesaw.chooser :as chooser]
            [seesaw.core :as ui]
            [seesaw.forms :as forms]
            [zdl-lex-client.http :as http]))

(defn open-dialog [{:keys [query total]} & args]
  (let [parent (some-> args first ui/to-root)]
    (if-let [csv-file (chooser/choose-file parent
                                           :type :save
                                           :filters [["CSV" ["csv"]]]
                                           :all-files? false)]
      (let [csv-extension? (.. csv-file (getName) (toLowerCase) (endsWith ".csv"))
            csv-file (if csv-extension? csv-file
                         (io/file (str (.. csv-file (getPath)) ".csv")))
            content (->> [(ui/label :text (format "%d Ergebnis(se)" total))
                          (ui/progress-bar :indeterminate? true)
                          (ui/label :text (str csv-file))]
                         (forms/forms-panel "center:150dlu" :items))
            dialog (ui/dialog :title "Ergebnisse exportieren"
                              :parent parent
                              :content content :options [])]
        (future
          (try
            (http/export query csv-file)
            (catch Exception e (ui/alert (.getMessage e)))
            (finally
              (ui/dispose! dialog))))
        (-> dialog (ui/pack!) (ui/show!))))))
