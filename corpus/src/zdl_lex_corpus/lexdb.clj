(ns zdl-lex-corpus.lexdb
  (:require [zdl-lex-common.url :refer [path->uri]]
            [zdl-lex-common.util :refer [->clean-map]]
            [clj-http.client :as http]
            [clojure.string :as str])
  (:import java.net.URI))

(defn- parse-query-response [{{:keys [names rows nrows sql]} :body}]
  (let [names (map keyword names)]
    {:query sql
     :total nrows
     :result (map (partial zipmap names) rows)}))

(defn query [corpus & {:keys [select from where groupby orderby limit offset]
                       :or {select "*" from "lex" limit 10}}]
  (->> {:method :get
        :as :json
        :url (.. (URI. "http://kaskade.dwds.de/dstar/")
                 (resolve (path->uri (str (name corpus) "/lexdb/export.perl")))
                 (toASCIIString))
        :query-params
        (->clean-map
         {"fmt" "json"
          "select" select
          "from" from
          "where" where
          "groupby" groupby
          "orderby" orderby
          "limit" limit
          "offset" offset})}
       (http/request)
       (parse-query-response)))

(defn remove-quotes [s]
  (str/replace s "\"" ""))

(defn frequencies [corpus lemmata]
  (let [lemmata (map #(str \" (remove-quotes %)  \") lemmata)
        select "l, SUM(f) as f"
        where (str "l in (" (str/join ", " lemmata) ")")
        groupby "l"
        limit (count lemmata)
        {:keys [result]} (query corpus
                                :select select :where where
                                :groupby groupby :limit limit)]
    (->> (for [{:keys [l f]} result] [l (Integer/parseInt f)])
         (into (sorted-map)))))

(comment
  (frequencies :ibk_web_2016c ["Test" "Datenbank" "Frequenz" "\"sdfjsdj"]))

