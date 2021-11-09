(ns zdl.lex.build.release
  (:require [clojure.tools.logging :as log]
            [zdl.lex.build.fs :refer [project-dir version-edn]]
            [zdl.lex.git :as git]
            [zdl.lex.util :refer [exec!]])
  (:import java.time.format.DateTimeFormatter
           java.time.OffsetDateTime))

(def ^:private ^DateTimeFormatter version-datetime-formatter
  (DateTimeFormatter/ofPattern "yyyyMM.dd.HH"))

(defn next'
  [_]
  (git/assert-clean project-dir)
  (let [version (.format version-datetime-formatter (OffsetDateTime/now))]
    (log/infof "Tagging next version '%s'" version)
    (git/sh! project-dir "tag" version)
    (spit version-edn (pr-str {:version version}) :encoding "UTF-8"))
  (System/exit 0))

(def next!
  (partial exec! next'))
