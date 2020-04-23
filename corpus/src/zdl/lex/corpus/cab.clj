(ns zdl.lex.corpus.cab
  (:require [clj-http.client :as http]
            [clojure.string :as str]))

(let [base-request {:method :post :url "http://data.dwds.de:9096/query" :as :json}]
  (defn query-lemmata [& forms]
    (let [params {"fmt" "json" "a" "norm1" "tokenize" 0 "q" (str/join " " forms)}
          request (assoc base-request :form-params params)
          {{:keys [body]} :body} (http/request request)
          tokens (some-> body first :tokens)]
      (zipmap (map :text tokens) (map #(get-in % [:moot :lemma]) tokens)))))

(comment
  (query-lemmata "Ärztepräsident", "Leerverkäufe" "Schifffahrtsgesellschaften"
         "Bevollmächtigter"))
