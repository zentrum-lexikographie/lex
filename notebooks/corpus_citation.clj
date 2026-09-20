(ns corpus-citation
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [gremid.xml :as gx]
   [nextjournal.clerk :as clerk]
   [virtuoso.core :as v]
   [zdl.lex.article :as article]
   [zdl.lex.git :as git]
   [zdl.lex.corpora.dstar :as dstar]))

(defn parse-cite-ref
  [s]
  (when (str/starts-with? s "dwds:")
    (some-> s (str/split #":") (second)
            (str/replace #"_((regional)|(primarily)|(secondarily))$" "")
            (str/replace #"^korpus21$" "kernbasis")
            (str/replace #"^spiegel_print$" "spie")
            (str/replace #"^tagesspiegel$" "tsp")
            (list))))

(def citations
  (binding [git/*dir* (fs/file (System/getProperty "user.home") "data" "zdl" "wb")]
    (->> (pmap article/read-xml (git/xml-files))
         (mapcat #(gx/elements :Lesart %))
         (mapcat #(gx/elements :Verwendungsbeispiele %))
         (mapcat #(gx/elements :Fundstelle %))
         (mapcat #(gx/attrs :Fundort %))
         (pmap parse-cite-ref)
         (mapcat identity)
         (frequencies))))

(defn corpus-info
  [[corpus endpoint]]
  (let [info       (dstar/ddc endpoint "info")
        subcorpora (tree-seq #(get % "corpora") #(get % "corpora") info)
        ntokens    (reduce + (map #(get % "ntokens" 0) subcorpora))
        descs      (keep #(get-in % ["user" "collectionInfo"]) subcorpora)
        desc       (first descs)]
    [corpus {:ntokens ntokens :desc desc}]))

(def corpus-infos
  (into (sorted-map) (v/pmap! corpus-info (dstar/corpora))))

(->> citations
     (keep (fn [[corpus citations]]
             (when-let [info (get corpus-infos corpus)]
               [(info :desc) (float (/ (* citations 1000000) (info :ntokens)))])))
     (sort-by (comp - last))
     (cons ["Corpus" "Citations per Million Tokens"])
     (clerk/use-headers)
     (clerk/table))
