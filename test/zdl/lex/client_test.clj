(ns zdl.lex.client-test
  (:require [clojure.test.check.generators :as gen]
            [clojure.test :refer :all]
            [clojure.zip :as zip]
            [gremid.data.xml :as dx]
            [gremid.data.xml.zip :as dxz]
            [manifold.deferred :as d]
            [zdl.lex.client :as client]
            [zdl.lex.server.gen.schema :as schema-gen]
            [zdl.lex.server.fixture :refer [backend-fixture]]
            [zdl.lex.timestamp :as ts]
            [clojure.tools.logging :as log]))

(def query-generator
  "Generate queries (prefix patterns combined with source filter)."
  (gen/fmap
   (fn [[c source]] (format "forms:%s* AND source:\"%s\"" c source))
   (gen/tuple gen/char-alpha (schema-gen/gen-source))))

(defn query-random-article
  [author query]
  (binding [client/*auth* [author author]]
    (log/infof "? %s [%s]" query author)
    (->
     (client/search-articles query :limit 1000)
     (d/chain :body :result #(when (seq %) (rand-nth %)) :id))))

(defn load-article
  [author id]
  (binding [client/*auth* [author author]]
    (log/infof "R %s [%s]" id author)
    (->
     (client/get-article id)
     (d/chain :body))))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(defn edit-article
  [author id xml]
  (let [timestamp (ts/format (java.time.LocalDate/now))]
    (log/infof "E %s [%s @ %s]" id author timestamp)
    (zip/root
     (zip/edit
      (dxz/xml1-> (zip/xml-zip xml) ::dwds/Artikel)
      (fn [a]
        (-> a
            (assoc-in [:attrs :Zeitstempel] timestamp)
            (assoc-in [:attrs :Autor] author)))))))

(defn save-article
  [author id xml]
  (binding [client/*auth* [author author]]
    (log/infof "W %s [%s]" id author)
    (->
     (client/post-article id (dx/emit-str xml)))))

(defn transact
  [[author query]]
  (d/chain
   (query-random-article author query)
   (fn [id]
     (when id
       (d/chain
        (load-article author id)
        #(edit-article author id %)
        #(save-article author id %))))))

(def num-edits
  256)

(use-fixtures :once backend-fixture)

(deftest edits
  (let [queries (gen/tuple (schema-gen/gen-author) query-generator)
        sample (gen/sample queries num-edits)]
    (is (deref (apply d/zip (map transact sample))))))
