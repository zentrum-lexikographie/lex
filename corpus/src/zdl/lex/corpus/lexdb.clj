(ns zdl.lex.corpus.lexdb
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [zdl.lex.util :refer [->clean-map path->uri]])
  (:import java.net.URI))

(defn query-lexdb [corpus & {:keys [select from where groupby orderby limit offset]
                             :or {select "*" from "lex" limit 10}}]
  (let [url (.. (URI. "http://kaskade.dwds.de/dstar/")
                (resolve (path->uri (str (name corpus) "/lexdb/export.perl")))
                (toASCIIString))
        params {"fmt" "json"
                "select" select "from" from
                "where" where
                "groupby" groupby "orderby" orderby
                "limit" limit "offset" offset}
        request {:method :post :url url :as :json}
        request (assoc request :form-params (->clean-map params))
        {{:keys [names rows nrows sql]} :body} (http/request request)
        names (map keyword names)]
    {:query sql
     :total nrows
     :result (map (partial zipmap names) rows)}))

(defn query-frequencies [corpus lemmata]
  (let [lemmata (map #(str \" (str/replace % "\"" "") \") lemmata)
        {:keys [result]} (query-lexdb
                          corpus
                          :select "l, SUM(f) as f"
                          :where (str "l in (" (str/join ", " lemmata) ")")
                          :groupby "l"
                          :limit (count lemmata))]
    (zipmap (map :l result) (map #(Integer/parseInt (:f %)) result))))

(comment
  (query-frequencies :ibk_web_2016c ["Test" "Datenbank" "Frequenz" "\"sdfjsdj"]))

