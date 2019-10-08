(ns zdl-lex-corpus.lexdb
  (:require [zdl-lex-common.url :refer [path->uri]]
            [zdl-lex-common.util :refer [->clean-map]]
            [clj-http.client :as http]
            [clojure.string :as str])
  (:import java.net.URI))

(defn query [corpus & {:keys [select from where groupby orderby limit offset]
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
        select "l, SUM(f) as f"
        where (str "l in (" (str/join ", " lemmata) ")")
        groupby "l"
        limit (count lemmata)
        response (query corpus :select select :where where :groupby groupby :limit limit)
        results (response :result)]
    (zipmap (map :l results) (map #(Integer/parseInt (:f %)) results))))

(comment
  (query-frequencies :ibk_web_2016c ["Test" "Datenbank" "Frequenz" "\"sdfjsdj"]))

