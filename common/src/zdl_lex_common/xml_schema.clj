(ns zdl-lex-common.xml-schema
  "RELAX NG schema parser, based on Kohsuke Kawaguchi's RNG-OM."
  (:require [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [zdl-lex-common.util :refer [file]]
            [zdl-lex-common.xml :as xml])
  (:import org.kohsuke.rngom.ast.util.CheckingSchemaBuilder
           org.kohsuke.rngom.digested.DSchemaBuilderImpl
           org.kohsuke.rngom.parse.xml.SAXParseable
           org.xml.sax.helpers.DefaultHandler))

(defn- name*
  "Parses a name class.

  Beware: Does not handle exception classes!"
  [nc]
  (let [names (transient #{})
        visitor (proxy [org.kohsuke.rngom.nc.NameClassWalker] []
                  (visitNsName [ns] (conj! names (str "{" ns "}")) nil)
                  (visitNsNameExcept [ns nc] (conj! names (str "{" ns "}")) nil)
                  (visitAnyName [] (conj! names "*") nil)
                  (visitAnyNameExcept [] (conj! names "*") nil)
                  (visitName [qn] (conj! names (str qn)) nil)
                  (visitNull [] (conj! names "") nil))]
    (.accept nc visitor)
    (persistent! names)))

(defn- parse*
  "Parses a pattern."
  [p]
  (let [clz (class p)
        type (-> clz .getSimpleName
                 (str/replace #"^D" "") (str/replace #"Pattern$" "")
                 (str/lower-case) (keyword))
        unary? (isa? clz org.kohsuke.rngom.digested.DUnaryPattern)
        container? (isa? clz org.kohsuke.rngom.digested.DContainerPattern)
        xml-token? (isa? clz org.kohsuke.rngom.digested.DXmlTokenPattern)]
    (merge {:tag type}
           (when xml-token? {:name (name* (.getName p))})
           (when unary? {:content (list (parse* (.getChild p)))})
           (when container? {:content (map parse* p)})
           (condp = type
             :grammar
             (let [start (some-> (.getStart p) parse*)
                   start (if start
                           (list {:tag :define
                                  :attrs {:name "start"}
                                  :content (list start)}))]
               {:content (seq (concat start (map parse* p)))})
             :define {:attrs {:name (.getName p)}
                      :content (list (parse* (.getPattern p)))}
             :ref {:attrs {:target (.getName p)}}
             :value {:attrs {:dt (.getType p) :value (.getValue p)}}
             :data {:attrs (into {:dt (.getType p)
                                  :except (some-> (.getExcept p) parse*)}
                                 (map #(vector (keyword (.getName %)) (.getValue %))
                                      (.getParams p)))}
             {}))))

(defn parse
  "Parses a schema in RELAX NG XML format.

   The resulting tree structure ressembles `clojure.xml` parse trees and
   can be processed with fns for such trees accordingly."
  [rng]
  (let [eh (proxy [DefaultHandler] [] (error [e] (throw e)))
        schema-builder (CheckingSchemaBuilder. (DSchemaBuilderImpl.) eh)
        schema (SAXParseable. (xml/->input-source rng) eh)]
    (parse* (.. schema (parse schema-builder)))))

(defn name=
  "Predicate for zipper locations, matching a given name with a name class."
  ([name]
   (partial name= name))
  ([name loc]
   (some-> loc zip/node :name (contains? name))))

(def ^:private >>
  "Shortcut notation for descendants of a zipper location."
  dz/descendants)

(defn- resolve-node
  "Resolves a reference pattern, avoiding cycles."
  [pdefs-by-name resolved {:keys [tag attrs content] :as node}]
  (let [target (some-> attrs :target)]
    (if (and (some? target) (= :ref tag) (not (resolved target)))
      (resolve-node pdefs-by-name (conj resolved target) (pdefs-by-name target))
      (assoc node :content
             (some->> content (map (partial resolve-node pdefs-by-name resolved)))))))

(defn resolve-refs
  "Resolve all reference patterns in a given schema."
  [schema]
  (let [pdefs (zx/xml-> (zip/xml-zip schema) >> :define zip/node)
        pdefs-by-name (zipmap (map #(get-in % [:attrs :name]) pdefs)
                              (map #(-> % :content first) pdefs))]
    (resolve-node pdefs-by-name #{} schema)))

(defn collect-values
  "Collects a set of values in a subtree selected by the given path."
  [& path]
  (into #{} (apply zx/xml-> (concat path [>> :value (zx/attr :value)]))))

(defn pdef?
  "Predicate for pattern definitions, matched by their name."
  ([name]
   (partial pdef? name))
  ([name loc]
   (and (->> loc zip/node :tag (= :define)) (= (zx/attr loc :name) name))))

(defn attr-of?
  "Predicate for attributes belonging to elements with the given name."
  [element-name]
  (fn [attr-loc]
    (if-let [parent-loc (zx/xml1-> attr-loc dz/ancestors [:element])]
      (name= element-name parent-loc))))

(defn -main [& args]
  (let [schema (->> (file "../oxygen/framework/rng/DWDSWB.rng")
                    (parse) (resolve-refs) (zip/xml-zip))
        article-attr? (attr-of? "{http://www.dwds.de/ns/1.0}Artikel")
        attr-values #(collect-values schema >> :attribute article-attr? (name= %))]
    (println
     {:sources (attr-values "Quelle")
      :authors (attr-values "Autor")
      :types (attr-values "Typ")
      :status (attr-values "Status")})))

(comment
  (->> (file "../oxygen/framework/rng/DWDSWB.rng") (parse) (resolve-refs)))


