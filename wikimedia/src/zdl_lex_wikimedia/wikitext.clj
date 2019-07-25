(ns zdl-lex-wikimedia.wikitext
  (:require [clojure.string :as str]
            [clojure.zip :as zip]
            [zdl-lex-wikimedia.util :refer [->clean-map]]
            [clojure.data.zip :as dz])
  (:import java.io.Writer
           org.sweble.wikitext.parser.ParserConfig
           [org.sweble.wikitext.parser.nodes WtExternalLink WtInternalLink WtNode WtTagExtension WtTemplate WtTemplateArgument WtText WtUrl WikitextNodeFactory]
           [org.sweble.wikitext.parser.utils SimpleParserConfig NonExpandingParser WtRtDataPrinter]))

(def parser (NonExpandingParser.))

(def ^WikitextNodeFactory node-factory
  (.. parser (getConfig) (getNodeFactory)))

(defn ^WtNode parse [^String content]
  (.parseArticle parser content ""))

(defmethod print-method WtNode [^WtNode v ^Writer w]
  (WtRtDataPrinter/print w v))

(defn zipper [^WtNode node]
  (zip/zipper
   (complement empty?)
   seq
   (fn [_ _] (throw (UnsupportedOperationException.)))
   node))

(defn camel->lisp [s]
  "https://gist.github.com/idmitrievsky/2b444ef94316dad2cd31452d4ab86871"
  (-> s
      (str/replace #"(.)([A-Z][a-z]+)" "$1-$2")
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      (str/lower-case)))

(defn node->type [^WtNode n]
  (-> (.getNodeName n)
      (camel->lisp)
      (str/replace #"^wt-" "")
      (keyword)))

(defn node->attrs [^WtNode node]
  (let [attrs (.getAttributes node)]
    (if-not (empty? attrs) attrs)))

(def ^:private excluded-prop?
  #{:rtd :warnings :entityMap :precededByNewline})

(defn- node->props [^WtNode node]
  (let [iter (.propertyIterator node)
        props (loop [props [] next? (.next iter)]
                (if next?
                  (recur (conj props [(keyword (.getName iter))
                                      (str (.getValue iter))])
                         (.next iter))
                  props))
        props (remove (comp excluded-prop? first) props)
        props (remove (comp empty? second) props)]
    (if-not (empty? props) (into {} props))))

(defmulti text class)

(defmethod text WtUrl [^WtUrl n] (str (.getProtocol n) ":" (.getPath n)))

(defmethod text WtText [^WtText n] (.getContent n))

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
    (or (instance? clz (-> loc zip/node))
        (filter #(and (zip/branch? %) (instance? clz (-> % zip/node)))
                (dz/children-auto loc)))))

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
