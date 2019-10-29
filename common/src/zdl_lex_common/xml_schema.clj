(ns zdl-lex-common.xml-schema
  (:require [zdl-lex-common.util :refer [file]]
            [zdl-lex-common.xml :as xml]
            [clojure.string :as str]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            [taoensso.timbre :as timbre])
  (:import org.kohsuke.rngom.ast.util.CheckingSchemaBuilder
           org.kohsuke.rngom.digested.DSchemaBuilderImpl
           org.kohsuke.rngom.parse.xml.SAXParseable
           org.xml.sax.helpers.DefaultHandler))

(defn- parse-name
  "Converts name classes.

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
  [p]
  (let [clz (class p)
        type (-> clz .getSimpleName
                 (str/replace #"^D" "") (str/replace #"Pattern$" "")
                 (str/lower-case) (keyword))
        unary? (isa? clz org.kohsuke.rngom.digested.DUnaryPattern)
        container? (isa? clz org.kohsuke.rngom.digested.DContainerPattern)
        xml-token? (isa? clz org.kohsuke.rngom.digested.DXmlTokenPattern)]
    (merge {:tag type}
           (when xml-token? {:name (parse-name (.getName p))})
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

(def ^:private sax-error-handler
  (proxy [DefaultHandler] []
    (error
      [e]
      (throw e))))

(defn parse
  [rng]
  (let [schema-builder (CheckingSchemaBuilder. (DSchemaBuilderImpl.) sax-error-handler)
        schema (SAXParseable. (xml/->input-source rng) sax-error-handler)]
    (parse* (.. schema (parse schema-builder)))))

(defn name=
  ([name]
   (partial name= name))
  ([name loc]
   (some-> loc zip/node :name (contains? name))))

(def ^:private >> dz/descendants)

(defn collect-values [& path]
  (->> (apply zx/xml-> (concat path [>> :value (zx/attr :value)]))
       (into (sorted-set))))

(defn pdef?
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


