(ns zdl-lex-server.api
  (:require [zdl-lex-server.solr :as solr]
            [ring.util.http-response :as htstatus]))

(defn form-suggestions [{{:keys [q]} :params}]
  (let [solr-response (solr/solr-suggest "forms" (or q ""))
        path-prefix [:body :suggest :forms (keyword q)]
        total (get-in solr-response (conj path-prefix :numFound) 0)
        suggestions (get-in solr-response (conj path-prefix :suggestions) [])]
    (htstatus/ok
     {:total total
      :result (for [{:keys [term payload]} suggestions]
                (merge {:suggestion term} (read-string payload)))})))
