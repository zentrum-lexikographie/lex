(ns zdl.lex.article.xml
  (:require
   [gremid.data.xml :as dx]
   [clojure.data.zip.xml :as zx]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.zip :as zip]
   [taoensso.tufte :as tufte :refer [defnp]]))

(defn read-xml
  [& args]
  (with-open [is (apply io/input-stream args)]
    (dx/pull-all (dx/parse is))))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(defnp normalize-text
  [s]
  (some-> s (str/replace #"\s+" " ") (str/trim) (not-empty)))

(declare text')

(defnp text'
  [node]
  (cond
    (string? node) node
    (map? node)    (let [{:keys [tag attrs content]} node]
                     (or
                      (condp = tag
                        ::dwds/Streichung ""
                        ::dwds/Loeschung  ""
                        ::dwds/Ziellesart ""
                        ::dwds/Ziellemma  (get attrs :Anzeigeform)
                        nil)
                      (apply str (map text' content))))
    :else          ""))

(defnp text
  [node]
  (normalize-text (text' node)))

(defnp zip-text
  [loc]
  (text (zip/node loc)))

(defnp zip-texts
  [locs]
  (remove nil? (map zip-text locs)))

(defnp hid
  [{{:keys [hidx]} :attrs :as node}]
  (apply str (cond-> [(normalize-text (zx/text (zip/xml-zip node)))]
               hidx (conj "#" hidx))))

(defnp zip-hid
  [loc]
  (hid (zip/node loc)))

(defnp zip-hids
  [locs]
  (remove nil? (map zip-hid locs)))
