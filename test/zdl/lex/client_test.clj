(ns zdl.lex.client-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is join-fixtures use-fixtures]]
   [clojure.test.check.generators :as gen]
   [clojure.tools.logging :as log]
   [clojure.zip :as zip]
   [gremid.data.xml :as dx]
   [gremid.data.xml.zip :as dx.zip]
   [zdl.lex.article :as article]
   [zdl.lex.client.http :as client.http]
   [zdl.lex.fixture.git :as fixture.git]
   [zdl.lex.fixture.index :as fixture.index]
   [zdl.lex.fixture.system :as fixture.system]
   [zdl.lex.schema :as schema]
   [zdl.lex.timestamp :as ts]
   [zdl.lex.url :as lexurl])
  (:import
   (java.net URL)))

(def query-generator
  "Generate queries (prefix patterns combined with source filter)."
  (gen/fmap
   (fn [[c source]] (format "forms:%s* AND source:\"%s\"" c source))
   (gen/tuple gen/char-alpha (schema/gen-source))))

(defn query-random-article
  [author query]
  (log/infof "? %s [%s]" query author)
  (let [req    {:url          "index"
                :query-params {:q     query
                               :limit "1000"}}
        resp   (client.http/request req)
        result (get-in resp [:body :result])]
    (when (seq result)
      (get (rand-nth result) :id))))

(defn load-article
  [author id]
  (log/infof "R %s [%s]" id author)
  (with-open [input (io/input-stream (URL. (str (lexurl/id->url id))))]
    (dx/pull-all (dx/parse input))))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(defn edit-article
  [author id xml]
  (let [timestamp (ts/format (java.time.LocalDate/now))]
    (log/infof "E %s [%s @ %s]" id author timestamp)
    (zip/root
     (zip/edit
      (dx.zip/xml1-> (zip/xml-zip xml) ::dwds/DWDS ::dwds/Artikel)
      (fn [a]
        (-> a
            (assoc-in [:attrs :Zeitstempel] timestamp)
            (assoc-in [:attrs :Autor] author)))))))

(defn save-article
  [author id xml]
  (log/infof "W %s [%s]" id author)
  (with-open [output (.. (URL. (str (lexurl/id->url id)))
                         (openConnection)
                         (getOutputStream))]
    (article/write-xml xml output)))

(defn transact
  [[author query]]
  (when-let [id (query-random-article author query)]
    (->> (load-article author id)
         (edit-article author id)
         (save-article author id))))

(def num-edits
  256)

(use-fixtures :once
  (join-fixtures [fixture.git/articles
                  fixture.system/instance
                  fixture.index/articles]))

(deftest edits
  (let [queries (gen/tuple (schema/gen-author) query-generator)
        sample  (gen/sample queries num-edits)]
    (is (coll? (doall (map transact sample))))))
