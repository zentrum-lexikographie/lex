(ns zdl.lex.version
  (:require [clojure.string :as str]
            [zdl.lex.fs :refer [version-edn]]
            [zdl.lex.git :as git]
            [zdl.lex.sh :as sh]
            [clojure.tools.logging :as log])
  (:import java.time.OffsetDateTime
           java.time.format.DateTimeFormatter))

(defn current
  []
  (if (git/dirty?)
    "000000.00.00"
    (let [versions (-> (git/sh! "tag" "--list") :out (str/split #"\n"))
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
  (git/assert-clean)
  (let [v (str "v" (next-version))]
    (log/info "Tagging next version '%s'" v)
    (git/sh! "tag" v)))

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
