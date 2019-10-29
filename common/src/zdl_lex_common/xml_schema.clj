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
                           (list {:type :define :attrs {:name "start"}
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

(defn collect-values
  "Collects a sorted set of values in a subtree selected by the given path."
  [& path]
  (->> (apply zx/xml-> (concat path [>> :value (zx/attr :value)]))
       (into (sorted-set))))

(defn pdef?
  "Predicate for pattern definitions, matched by their name."
  ([name]
   (partial pdef? name))
  ([name loc]
   (and (->> loc zip/node :tag (= :define)) (= (zx/attr loc :name) name))))

(comment
  (->> (file "../oxygen/framework/rng/DWDSWB.rng") (parse))

  (let [schema (->> (file "../oxygen/framework/rng/DWDSWB.rng") (parse) (zip/xml-zip))
        values (partial collect-values schema >>)]
    {:sources (values (pdef? "Metadaten.allgemein") >> :attribute (name= "Quelle"))
     :authors (values (pdef? "Mitarbeiterinnen"))
     :types (values (pdef? "Metadaten.Artikel") >> :attribute (name= "Typ"))}))


