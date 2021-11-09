(ns zdl.lex.article.xml
  (:require [clojure.data.xml :as dx]
            [clojure.data.xml.tree :as dxt]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [taoensso.tufte :as tufte :refer [defnp]]))

(defn read-xml
  [& args]
  (with-open [is (apply io/input-stream args)]
    (dxt/event-tree (doall (dx/event-seq is {})))))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(declare text*)

(defn text-content*
  [{:keys [content]}]
  (apply str (map text* content)))

(defnp text*
  [node]
  (cond
    (string? node) node
    (map? node)    (let [{:keys [tag attrs]} node]
                     (or
                      (condp = tag
                        ::dwds/Streichung ""
                        ::dwds/Loeschung  ""
                        ::dwds/Ziellesart ""
                        ::dwds/Ziellemma  (get attrs :Anzeigeform)
                        nil)
                      (text-content* node)))
    :else          ""))

(defnp normalize-text
  [s]
  (some-> s (str/replace #"\s+" " ") (str/trim) (not-empty)))

(defnp text
  [node]
  (normalize-text (text* node)))

(defnp zip-text
  [loc]
  (text (zip/node loc)))

(defnp zip-texts
  [locs]
  (remove nil? (map zip-text locs)))

(defnp hid
  [{{:keys [hidx]} :attrs :as node}]
  (apply str (cond-> [(text node)] hidx (conj "#" hidx))))

(defnp zip-hid
  [loc]
  (hid (zip/node loc)))

(defnp zip-hids
  [locs]
  (remove nil? (map zip-hid locs)))
