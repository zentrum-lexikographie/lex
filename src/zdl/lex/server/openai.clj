(ns zdl.lex.server.openai
  (:require
   [zdl.lex.env :as env]
   [clj-http.client :as http]
   [jsonista.core :as json]
   [selmer.parser :as selmer]
   [zdl.lex.server.korap :as korap]))

(defn complete
  [chat]
  (->
   env/openai-api-request
   (update :body (comp json/write-value-as-string #(assoc % "messages" chat)))
   (http/request)
   (update :body json/read-value)))

(def persona-message
  {"role"    "system"
   "content" "Du bist ein Lexikograph und gibst kurze, genaue Antworten!"})

(defn lexicographer-chat
  [& messages]
  (->
   (into [persona-message] (map #(hash-map "role" "user" "content" %)) messages)
   (complete)
   (get-in [:body "choices"]) (first) (get-in ["message" "content"])))

(def gloss-from-examples-instruction-template
  "zdl/lex/server/openai/gloss-from-examples-instruction.txt")

(defn gloss-from-examples
  [term examples]
  (->>
   {:term term :examples examples}
   (selmer/render-file gloss-from-examples-instruction-template)
   (lexicographer-chat)))

(comment
  (let [examples (korap/dnb-query "[base=\"Abriss\"]")
        examples (into [] (comp (map :text) (take 100)) examples)]
    (println (gloss-from-examples "Abriss" examples))))
