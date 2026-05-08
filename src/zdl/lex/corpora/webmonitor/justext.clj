(ns zdl.lex.corpora.webmonitor.justext
  (:require
   [gremid.xml :as gx]
   [clojure.string :as str]
   [medley.core :refer [dedupe-by]]
   [zdl.lex.util :refer [lines-resource]]))

(def paragraph-tags
  #{:address :article :aside
    :body :blockquote
    :canvas :caption :center :col :colgroup
    :dd :div :dl :dt
    :fieldset :figcaption :figure :footer :form
    :h1 :h2 :h3 :h4 :h5 :h6 :header
    :img
    :legend :li
    :main
    :nav :noscript
    :ol :optgroup :option
    :p :pre
    :section
    :table :td :textarea :tfoot :th :thead :tr
    :ul
    :video})

(def min-length
  70)

(def max-length
  200)

(def max-heading-distance
  200)

(def max-link-density
  0.2)

(def low-stop-word-density
  0.3)

(def high-stop-word-density
  0.32)

(def stop-words
  (into #{}
        (map str/lower-case)
        (lines-resource "zdl/lex/corpora/webmonitor/stopwords.txt")))

(defn node->paragraphs
  ([node]
   (node->paragraphs [] node))
  ([path {:keys [tag content] :as node}]
   (let [path (conj path tag)]
     (or (seq (mapcat #(node->paragraphs path %) content))
         (when (paragraph-tags tag) (list {:node node :path path}))))))

(defn node->text
  [node]
  (or
   (when (string? node) node)
   (let [{:keys [tag content]} node]
     (or (when (= :br tag) "\n")
         (when (#{:style :script} tag) "")
         (not-empty (str/join (map node->text content)))
         ""))))

(defn classify-by-tag
  [{:keys [path] {:keys [tag]} :node}]
  (cond
    (= :img tag)                           :bad
    (some #{:form :noscript :select} path) :bad))

(defn classify-by-text
  [text]
  (when (or (str/includes? text "©") (str/includes? text "&copy;")) :bad))

(defn classify-by-links
  [length {:keys [node]}]
  (let [short?      (< length min-length)
        link-length (reduce + (map (comp count gx/text) (gx/elements :a node)))
        links?      (pos? link-length)
        link-ratio  (/ link-length length)]
    (cond
      (and short? links?)             :bad
      (> link-ratio max-link-density) :bad
      short?                          :short)))

(defn classify-by-stop-words
  [length text]
  (let [words           (into [] (map str/lower-case) (str/split text #"\s"))
        stop-word-count (count (filter stop-words words))
        stop-word-ratio (/ stop-word-count (count words))]
    (if (>= stop-word-ratio high-stop-word-density)
      (if (> length max-length)
        :good
        :near-good)
      (when (>= stop-word-ratio low-stop-word-density)
        :near-good))))

(defn classify
  [{:keys [node] :as para}]
  (when-let [text (some-> node node->text (str/replace " " " ") gx/normalize-ws)]
    (let [length (count text)]
      (list
       (assoc para
              :text text
              :class (or (classify-by-tag para)
                         (classify-by-text text)
                         (classify-by-links length para)
                         (classify-by-stop-words length text)
                         :bad))))))


(defn classify-by-context
  [classify paras]
  (let [length (count paras)]
    (into
     []
     (map #(classify
            (paras %)
           (concat (map paras (range (dec %) 0 -1)) (list {:class :bad}))
           (concat (map paras (range (inc %) length)) (list {:class :bad}))))
     (range length))))

(defn classify-headings
  [class-pred revised-class paras]
  (classify-by-context
   (fn [{clazz :class {:keys [tag]} :node :as para} _prevs nexts]
     (if (and (#{:h1 :h2 :h3 :h4 :h5 :h6} tag)
              (class-pred clazz)
              (some #(= :good (% :class)) nexts))
       (let [h-distance (->> (take-while #(not= :good (% :class)) nexts)
                             (map (comp count :text)) (reduce +))]
         (cond-> para
           (<= h-distance max-heading-distance) (assoc :class revised-class)))
       para))
   paras))

(defn near-good?
  [{clazz :class}]
  (= :near-good clazz))

(def classify-shorts
  (partial
   classify-by-context
   (fn [{clazz :class :as para} prevs nexts]
     (cond-> para
       (= :short clazz)
       (assoc
        :class
        (let [prev (first (map :class (remove near-good? prevs)))
              next (first (map :class (remove near-good? nexts)))]
          (if (and (= :good prev) (= :good next))
            :good
            (if (and (= :bad prev)  (= :bad next))
              :bad
              (let [prev (first (map :class prevs))
                    next (first (map :class nexts))]
                (if (or (= :near-good prev) (= :near-good next))
                  :good
                  :bad))))))))))

(def classify-near-goods
  (partial
   classify-by-context
   (fn [{clazz :class :as para} prevs nexts]
     (cond-> para
       (= :near-good clazz)
       (assoc
        :class
        (let [prev (first (map :class (remove near-good? prevs)))
              next (first (map :class (remove near-good? nexts)))]
          (if (and (= :bad prev)  (= :bad next))
            :bad
            :good)))))))

(defn paragraphs
  [html]
  (when-let [body (gx/element :body html)]
    (let [paras  (into [] (mapcat classify) (node->paragraphs body))]
      (->>  paras
            (classify-headings #{:short} :near-good)
            (classify-shorts)
            (classify-near-goods)
            (classify-headings #{:bad :near-good} :good)
            (filter #(= :good (% :class)))
            (dedupe-by :text)
            (map (juxt (comp :tag :node) :text))))))
