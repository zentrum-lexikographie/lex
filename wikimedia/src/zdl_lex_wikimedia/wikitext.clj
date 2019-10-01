(ns zdl-lex-wikimedia.wikitext
  (:require [clojure.data.zip :as dz]
            [clojure.string :as str]
            [clojure.zip :as zip])
  (:import java.io.Writer
           [org.sweble.wikitext.parser.nodes WikitextNodeFactory WtExternalLink WtInternalLink WtNode WtTemplate WtTemplateArgument WtText WtUrl]
           org.sweble.wikitext.parser.ParserConfig
           [org.sweble.wikitext.parser.utils NonExpandingParser WtRtDataPrinter]))

(def ^NonExpandingParser parser (NonExpandingParser.))

(def ^WikitextNodeFactory node-factory
  (.getNodeFactory ^ParserConfig (.getConfig parser)))

(defn ^WtNode parse [^String content]
  (.parseArticle parser content ""))

(defmethod print-method WtNode [^WtNode v ^Writer w]
  (WtRtDataPrinter/print w v))

(defn zipper [^WtNode node]
  "A read-only zipper for the given Wikitext node."
  (zip/zipper
   (complement empty?)
   seq
   (fn [_ _] (throw (UnsupportedOperationException.)))
   node))

(defmulti text class)

(defmethod text WtUrl [^WtUrl n] (str (.getProtocol n) ":" (.getPath n)))

(defmethod text WtText [^WtText n] (.getContent n))

(def ^:private visible-templates
  #{"kPl."})

(defmethod text WtTemplate [^WtTemplate t]
  (let [name (not-empty (text (.getName t)))
        name (if (visible-templates name) name)
        args (not-empty (str/join ", " (map text (.getArgs t))))]
    (str/join ": " (remove nil? [name args]))))

(defmethod text WtTemplateArgument [^WtTemplateArgument arg]
  (->> [(.getName arg) (.getValue arg)]
       (map text)
       (remove empty?)
       (str/join ": ")))

(defmethod text WtExternalLink [^WtExternalLink n]
  (text (if (.hasTitle n) (.getTitle n) (.getTarget n))))

(defmethod text WtInternalLink [^WtInternalLink n]
  (let [t (text (if (.hasTitle n) (.getTitle n) (.getTarget n)))
        pre (.getPrefix n)
        post (.getPostfix n)]
    (str pre t post)))

(defmethod text WtNode [^WtNode n] (apply str (map text n)))

(defn loc->text [loc]
  (-> loc zip/node text))

(defn class= [clz]
  (fn [loc]
    (filter #(instance? clz (zip/node %))
            (if (dz/auto? loc)
              (dz/children-auto loc)
              (list (dz/auto true loc))))))

(defn text= [s]
  (fn [loc]
    (= s (loc->text loc))))

(declare nodes->)

(defn seq-test [preds]
  (fn [loc]
    (and (seq (apply nodes-> loc preds)) (list loc))))

(defn nodes-> [loc & preds]
  (dz/mapcat-chain loc preds
                   #(cond (class? %) (class= %)
                          (string? %) (text= %)
                          (vector? %) (seq-test %))))


(defn node-> [loc & preds]
  (first (apply nodes-> loc preds)))
