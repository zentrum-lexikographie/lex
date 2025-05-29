(ns zdl.lex.test-data
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.walk]
   [gremid.xml :as gx]
   [medley.core :refer [distinct-by]]
   [clojure.string :as str]
   [taoensso.telemere :as tm]))

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

(defn article-element?
  [{:keys [tag]}]
  (= :Artikel tag))

(defn random-name
  ([]
   (random-name "xyz"))
  ([v]
   (when (not-empty v) (rand-nth ["dsanders" "jgrimm" "wgrimm" "kduden"]))))

(defn anonymize*
  [v]
  (cond-> v
    (article-element? v)      (->
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
        (tm/log! :info (str test-data-path))
        (-> test-data-path fs/parent fs/create-dirs)
        (with-open [os (io/output-stream (fs/file test-data-path))]
          (gx/write-events os (gx/node->events xml)))))))

(defn -main
  [dir]
  (try (create! dir) (finally (shutdown-agents))))

(comment
  (let [dir (fs/file "/home/gregor/data/zdl/wb")]
    (->>
     (file-seq dir)
     (filter (every-pred #(.isFile %) #(.. % (getName) (endsWith ".xml"))))
     (pmap (partial parse-article dir))
     (filter (every-pred
              (comp (partial = "Red-f") :Status)
              #(str/starts-with? (:Zeitstempel %) "2025-05")))
     (mapcat (comp (partial gx/elements :Lesart) ::xml))
     (mapcat (partial gx/elements :Beleg))
     (filter #(= "good_example" (gx/attr :class %)))
     (map #(str/replace (with-out-str (gx/write-node *out* %)) #"\s+" " "))
     (map #(str/replace % #"^<\?xml.+?\?>\n?" ""))
     (str/join \newline)
     (spit (fs/file "good_examples_202505.xmll")))))
