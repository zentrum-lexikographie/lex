(ns zdl-lex-client.results
  (:require [seesaw.bind :as uib]
            [zdl-lex-client.bus :as bus]
            [seesaw.core :as ui]
            [taoensso.timbre :as timbre]
            [clojure.set :as cs]))

(def output (ui/tabbed-panel :tabs []))

(defn change-results [results]
  (let [tabs-by-id (group-by #(ui/config % :id) (ui/select output [:.result]))
        tab-ids (keys tabs-by-id)

        results-by-id (group-by (comp keyword :id) results)
        result-ids (keys results-by-id)

        new-ids (remove (apply hash-set tab-ids) result-ids)
        removed-ids (remove (apply hash-set result-ids) tab-ids)]

    (ui/invoke-soon
     (doseq [id removed-ids :let [tab (-> id tabs-by-id first)]]
       (.remove output tab))
     (doseq [id (reverse new-ids) :let [result (-> id results-by-id first)
                                        id (name id)]]
       (.insertTab output id nil (ui/label :id (keyword id)
                                           :class :result :text id) id 0)
       (.setSelectedIndex output 0)))))

;;(doseq [res (ui/select output [:.result])] (.remove output res))
;;(defonce change-handler (uib/subscribe bus/search-results change-results))
