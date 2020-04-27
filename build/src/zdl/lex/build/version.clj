(ns zdl.lex.build.version
  (:require [clojure.string :as str]
            [zdl.lex.build.fs :refer [project-dir version-edn]]
            [zdl.lex.git :as git]
            [zdl.lex.sh :refer [sh!]]
            [clojure.tools.logging :as log])
  (:import java.time.OffsetDateTime
           java.time.format.DateTimeFormatter))

(defn current
  []
  (if (git/dirty? project-dir)
    "000000.00.00"
    (let [versions (-> (git/sh! project-dir "tag" "-l") :out (str/split #"\n"))
          versions (map (comp #(str/replace % #"^v" "") str/trim) versions)
          versions (remove #{""} versions)
          versions (sort #(compare %2 %1) versions)]
      (or (first versions) "000000.00.00"))))

(def ^:private ^DateTimeFormatter version-datetime-formatter
  (DateTimeFormatter/ofPattern "yyyyMM.dd.HH"))

(defn next-version
  []
  (.format version-datetime-formatter (OffsetDateTime/now)))

(defn tag-next!
  []
  (git/assert-clean project-dir)
  (let [v (str "v" (next-version))]
    (log/infof "Tagging next version '%s'" v)
    (git/sh! project-dir "tag" v)))

(defn write!
  []
  (let [version {:version (current)}]
    (log/info version)
    (spit version-edn (pr-str version) :encoding "UTF-8")))

(defn -main
  [& [action]]
  (try
    (condp = action
      "next" (tag-next!)
      (do
        (.. (org.apache.log4j.Logger/getRootLogger)
            (setLevel org.apache.log4j.Level/WARN))
        (println (current))))
    (finally (shutdown-agents))))
