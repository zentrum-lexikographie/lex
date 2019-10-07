(ns zdl-lex-corpus.cab
  (:require [clj-http.client :as http]))

(defn query [q]
  (http/request {:method :post
                 :as :json
                 :url "http://data.dwds.de:9096/query"
                 :form-params
                 {"fmt" "json"
                  "a" "expand"
                  "tokenize" 0
                  "q" q}}))

(comment
  (query "Ärztepräsident"))
