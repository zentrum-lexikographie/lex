(ns zdl.lex.server.anchors
  (:require  [clojure.test :refer :all]
             [clojure.walk :refer [postwalk]]
             [zdl.nlp :as nlp]
             [zdl.lex.article :as article]
             [mount.core :as mount]
             [clojure.data.csv :as csv]
             [clojure.java.io :as io]
             [clojure.string :as str]))

(defn senses->glosses
  [v]
  (if (map? v) (concat (list (:gloss v)) (:subsenses v)) v))

(def token-attrs
  (juxt #_:idx :lemma :pos :dependency))

(defn dep-path
  [sentence {:keys [governor] :as token}]
  (concat [token] (if governor (dep-path sentence (nth sentence governor)))))

(defn- head-token?
  [[_ _ rel]]
  (nil? rel))

(defn- remove-parens
  [s]
  (-> s
      (str/replace #"\([^)]+\)" "")
      (str/replace #"\s+" " ")
      (str/trim)))

(defn analyze-gloss
  [gloss]
  (->
   (->>
    (for [sentence (->> gloss remove-parens nlp/annotate)
          {:keys [lemma] :as token} sentence :when lemma]
      (->>
       (map token-attrs (dep-path sentence token))
       (remove (comp (some-fn #{:appr :art :apprart :kon :ne :prels}
                              #_(-> % name first #{\v}))
                     second))
       (distinct)))
    #_(filter (partial some head-token?))
    (sort-by count #(compare %2 %1))
    (first)
    (reverse)
    (map first)
    #_(take 2)
    (vec))
   (vector gloss)))

(defn analyze-senses
  [{:keys [id forms senses pos]}]
  (if-let [glosses (seq (flatten (postwalk senses->glosses senses)))]
    (let [head [id (first forms) (first pos)]
          glosses (map analyze-gloss glosses)
          dupes? (->> glosses (map first) (frequencies) (vals) (some (partial < 1)))
          head [(if dupes? "DUP!") id (first forms) (first pos)]]
      (for [gloss glosses] (concat head gloss)))))

(defn analysis->csv
  [records]
  (with-open [w (io/writer (io/file "anchors.csv"))]
    (csv/write-csv w records)))

(comment
  (mount/start #'nlp/mate-pipeline)
  (mount/stop)
  (->> (article/articles "../../zdl-wb")
       (filter :senses)
       (take 3))
  (->> (article/articles "../../zdl-wb")
       #_(drop 100)
       (mapcat analyze-senses)
       (take 100)
       (analysis->csv))
  (nlp/annotate "Zusammenschluss zur Erreichung gemeinsamer Zwecke")
  (nlp/annotate "Teil der offiziellen Bezeichnung der Streitkräfte einiger sozialistischer Staaten und revolutionärer Bewegungen")
  (nlp/annotate "auf die Unterstützung oder Bewaffnung weiter Teile der Bevölkerung sich gründende Armee"))

