(ns zdl.lex.qa
  (:require
   [clojure.string :as str]
   [gremid.xml :as gx]
   [gremid.xml-schema :as gxs]
   [taoensso.telemere :as tel]
   [zdl.lex.lock :as lock :refer [with-lock]]
   [zdl.lex.git :as git]
   [zdl.lex.article :as article]
   [zdl.lex.article.typography :as article.typography]
   [babashka.fs :as fs]))

(def rng-validate
  (->> (gxs/->rng-schema (fs/file "oxygen" "framework" "rng" "DWDSWB.rng"))
       (partial gxs/rng-validate)))

(def sch-validate
  (->> (gxs/->xslt (fs/file  "oxygen" "framework" "rng" "DWDSWB.sch.xsl"))
       (partial gxs/sch-validate)))


(defn check-for-errors
  [xml file]
  {:errors
   (cond-> []
     (seq (article.typography/check xml)) (conj "Typographie")
     (seq (rng-validate file))            (conj "Schema")
     (seq (sch-validate file))            (conj "Schematron"))})

(comment
  (rng-validate (fs/file "src" "template.xml"))
  (sch-validate (fs/file "src" "template.xml")))

(defn red-1->red-2
  [node]
  (if (= :Artikel (:tag node))
    (assoc-in node [:attrs :Status] "Red-2")
    (if (string? node)
      node
      (update node :content (partial map red-1->red-2)))))

(defn edit
  [path]
  (try
    (let [xml     (article/read-xml (fs/file git/*dir* path))
          article (gx/element :Artikel xml)]
      (when (= "Red-1" (gx/attr :Status article))
        (let [wdg?   (str/includes? (or (gx/attr :Quelle article) "") "WDG")
              edited (cond-> (article.typography/fix xml)
                       (not wdg?) (article.typography/enumerate-senses))
              edited (red-1->red-2 edited)]
          (when-not (= xml edited) edited))))
    (catch Throwable _)))

(defn edit-article!
  [path]
  (try
    (binding [lock/*context* {:owner    "zdl-lex-server"
                              :resource path
                              :token    (str (random-uuid))}]
      (with-lock
        (fn []
          (when-let [edited (edit path)]
            (git/write-article-file path #(article/write-xml edited %))))))
    (catch Throwable t
      ;; Skip locked articles
      (if (lock/locked? t) (tel/error! t) (throw t)))))

(defn edit-articles!
  []
  (run! edit-article! (git/xml-paths)))
