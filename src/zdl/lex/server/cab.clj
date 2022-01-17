(ns zdl.lex.server.cab
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [jsonista.core :as json]
            [zdl.lex.article :as article]
            [zdl.lex.server.git :as server.git]))

(defn read-json
  [response]
  (update response :body json/read-value json/keyword-keys-object-mapper))

(defn cab-response->lemma-map
  [{{[{:keys [tokens]}] :body} :body}]
  (reduce
   (fn [m {{:keys [word analyses]} :moot}]
     (assoc m word (vec (map #(select-keys % [:lemma :prob]) analyses))))
   nil
   tokens))

(defn lemmatize
  [forms]
  (-> {:method      :post
       :url         "http://data.dwds.de:9096/query"
       :form-params {:a        "lemma1"
                     :fmt      "json"
                     :tokenize "0"
                     :q       (str/join " " forms)}}
      (http/request)
      (update :body json/read-value json/keyword-keys-object-mapper)
      (cab-response->lemma-map)))

(comment
  (let [lemmatize' (comp (mapcat :forms) (partition-all 10) (map lemmatize) (take 2))]
    (into [] lemmatize' (article/extract-articles-from-files server.git/dir))))
