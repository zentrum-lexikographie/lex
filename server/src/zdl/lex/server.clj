(ns zdl.lex.server
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.data :as data]
            [zdl.lex.fs :refer [path]]
            [zdl.lex.server.http :as http]
            [zdl.lex.server.metrics :as metrics]))

(def cli-args
  [["-d" "--data DATA_DIR"
    "Path of data directory (git checkout, db files etc.)"
    :id "ZDL_LEX_DATA_DIR"
    :default (getenv "ZDL_LEX_DATA_DIR" "zdl-lex-data")]
   ["-p" "--port HTTP_PORT"
    "HTTP port for embedded server"
    :id "ZDL_LEX_HTTP_PORT"
    :default (getenv "ZDL_LEX_HTTP_PORT" "3000")]
   ["-m" "--metrics MINS" "Report metrics every MIN minutes (0 = disabled)"
    :id "ZDL_LEX_METRICS_REPORT_INTERVAL"
    :default (getenv "ZDL_LEX_METRICS_REPORT_INTERVAL" "0")]
   ["-h" "--help"]])

(def year
  (.getYear (java.time.LocalDate/now)))

(defn usage [options-summary]
  (->> ["ZDL/Lex Server"
        (str "Copyright (C) "
             year
             " Berlin-Brandenburgische Akademie der Wissenschaften")
        ""
        "Usage: java -jar org.zdl.lex.server.jar [OPTION]..."
        ""
        "Options:"
        options-summary
        ""
        "This program is free software: you can redistribute it and/or modify"
        "it under the terms of the GNU General Public License as published by"
        "the Free Software Foundation, either version 3 of the License, or"
        "(at your option) any later version."
        ""
        "This program is distributed in the hope that it will be useful,"
        "but WITHOUT ANY WARRANTY; without even the implied warranty of"
        "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the"
        "GNU General Public License for more details."
        ""
        "You should have received a copy of the GNU General Public License"
        "along with this program. If not, see <https://www.gnu.org/licenses/>."]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred:\n\n"
       (str/join \newline errors)
       "\n\n(See --help for usage instructions.)"))

(defn exit
  ([status]
   (exit status ""))
  ([status msg]
   (when (not-empty msg) (println msg))
   (System/exit status)))

(defn start
  ([]
   (start {}))
  ([args]
   (mount/start (mount/with-args args))
   (log/infof "Started ZDL/Lex Server @[%s]" (path (data/dir)))))

(defn stop
  []
  (log/info "Stopping ZDL/Lex Server")
  (mount/stop))

(comment
  (parse-opts ["-p" "2000"] cli-args))

(defn -main
  [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-args)]
    (cond
      (:help options)
      (exit 0 (usage summary))
      errors
      (exit 1 (error-msg errors))
      :else
      (try
        (.. (Runtime/getRuntime)
            (addShutdownHook (Thread. (partial stop))))
        (start options)
        (.. (Thread/currentThread) (join))
        (catch Throwable t
          (.printStackTrace t)
          (exit 2 (.getMessage t)))))))
