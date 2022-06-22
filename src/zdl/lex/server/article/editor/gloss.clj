(ns zdl.lex.server.article.editor.gloss
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [gremid.data.xml :as dx]
   [zdl.lex.article :as article]
   [zdl.lex.server.article.editor.util :refer [edit-xml! red-1?]]))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(defn sense?
  [node]
  (= ::dwds/Lesart (:tag node)))

(defn assoc-sense-num'
  [[content next-sense-num] node]
  (if (sense? node)
    (let [node (assoc node ::n next-sense-num)]
      [(conj content node) (inc next-sense-num)])
    [(conj content node) next-sense-num]))

(defn assoc-sense-nums'
  [{:keys [content] :as node}]
  (if-not content
    node
    (let [content (map assoc-sense-nums' content)]
      (if-not (second (filter sense? content))
        (assoc node :content content)
        (let [content (first (reduce assoc-sense-num' [[] 0] content))]
          (assoc node :content content))))))

(def nums
  [(into [] (map #(format "%d." %) (range 1 30)))
   (into [] (map #(format "%s)" %) "abcdefghijklmnopqrstuvwxyz"))
   (into [] (map #(format "%s)" %) "αβγδεζηθικλμνξοπρστυφχψω"))])

(defn enumerate-senses
  ([node]
   (enumerate-senses 0 (assoc-sense-nums' node)))
  ([level {::keys [n] :keys [content] :as node}]
   (cond-> node
     (and (<= level 1) n) (assoc-in [:attrs :n] (get-in nums [level n]))
     (and (>  level 1) n) (assoc-in [:attrs :n] nil)
     content              (update
                           :content
                           (partial
                            map
                            (partial
                             enumerate-senses
                             (cond-> level (sense? node) (inc))))))))

(defn wdg?
  [{:keys [source]}]
  (str/includes? (or source "") "WDG"))

(defn edit!
  [article f]
  (when (and (red-1? article) (not (wdg? article)))
    (log/infof "! %s" (article/desc article))
    (edit-xml! article f enumerate-senses)))
