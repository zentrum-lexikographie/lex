(ns zdl.lex.dev.test-data
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.walk]
   [gremid.xml :as gx]
   [medley.core :refer [distinct-by]]
   [clojure.string :as str]
   [taoensso.telemere :as tel]))

(defn parse-article
  [dir f]
  (with-open [is (io/input-stream f)]
    (let [xml     (->> is gx/read-events gx/events->node)
          article (gx/element :Artikel xml)]
      (assoc (article :attrs)
             ::path (str (fs/relativize dir f))
             ::pos (some->> article (gx/element :Wortklasse) (gx/text))
             ::xml xml))))

(def sample-subset?
  (every-pred (comp #{"Adjektiv" "Substantiv" "Verb" "Mehrwortausdruck"} ::pos)
              (complement #(str/includes? (:Quelle %) "Duden"))))

(def fingerprint
  (juxt :Typ :Status ::pos))

(defn oxygen-comment-start?
  [{:keys [target]}]
  (= "oxy_comment_start" target))

(defn authored-element?
  [{{author :Autor editor :Redakteur} :attrs}]
  (or author editor))

(defn random-name
  ([]
   (random-name "xyz"))
  ([v]
   (when (not-empty v) (rand-nth ["dsanders" "jgrimm" "wgrimm" "kduden"]))))

(defn anonymize*
  [v]
  (cond-> v
    (authored-element? v)     (->
                                (update-in [:attrs :Autor] random-name)
                                (update-in [:attrs :Redakteur] random-name))
    (oxygen-comment-start? v) (update
                               :data str/replace #"author=\"[^\"]+\""
                               (str "author=\"" (random-name) "\""))))

(defn anonymize
  [article]
  (update article ::xml (partial clojure.walk/postwalk anonymize*)))

(defn sample
  [dir]
  (->> (file-seq dir)
       (filter (every-pred #(.isFile %) #(.. % (getName) (endsWith ".xml"))))
       (shuffle)
       (pmap (partial parse-article dir))
       (filter sample-subset?)
       (distinct-by fingerprint)
       (map anonymize)))

(defn create!
  [dir]
  (let [test-data-dir (doto (fs/path "test" "data")
                        (fs/delete-tree)
                        (fs/create-dirs))]
    (doseq [{::keys [xml path]} (sample (io/file dir))]
      (let [test-data-path (fs/path test-data-dir path)]
        (tel/log! :info (str "+ " test-data-path))
        (-> test-data-path fs/parent fs/create-dirs)
        (with-open [os (io/output-stream (fs/file test-data-path))]
          (gx/write-events os (gx/node->events xml)))))))

(defn -main
  [dir]
  (try (create! dir) (finally (shutdown-agents))))
