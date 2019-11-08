(ns zdl-lex-build.exist
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.args :as args]
            [zdl-lex-common.log :as log]))

(defn sh->out [& args]
  (timbre/debugf "sh: %s" args)
  (let [{:keys [exit out] :as result} (apply shell/sh args)]
    (when-not (= 0 exit) (throw (ex-info (pr-str args) result)))
    out))

(let [xml-db-host "spock.dwds.de"]
  (defn ssh-xml-db [& args]
    (apply sh->out (concat ["ssh" xml-db-host] args)))

  (defn rsync-xml-db [source dest]
    (sh->out "rsync" "-a" (str xml-db-host ":" source) dest)))

(defn parse-backup-sig [sig]
  (if-let [sig (re-find #"((?:inc)|(?:full))(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})" sig)]
    (let [[sig type year month day hour minute] sig]
      {:sig sig
       :type type
       :date-time
       (java.time.LocalDateTime/of
        (Integer/parseInt year)
        (Integer/parseInt month)
        (Integer/parseInt day)
        (Integer/parseInt hour)
        (Integer/parseInt minute))})))


(def export-dir "/home/eXist-db-2.2/webapp/WEB-INF/data/export")

(defn get-active-backup-chain []
  (->> (-> (ssh-xml-db "ls" export-dir) (str/split #"\n"))
       (remove empty?)
       (map parse-backup-sig)
       (remove nil?)
       (sort-by :date-time) ; chronological order
       (partition-by :type) ; alternating full/incremental backups
       (take-last 2) ; last 2 sets of full and incremental backups
       (drop-while (comp (partial = "inc") :type first)) ; drop leading incremental
       (flatten)))

(def parse-args
  (partial args/parse
           [["-l" "--list" "Just list the imported backup sets"]
            ["-h" "--help"]]))

(defn run [& args]
  (let [{:keys [options]} (parse-args args)
        backups (get-active-backup-chain)]
    (if (:list options)
      (->> backups
           (map (fn [{:keys [type date-time]}] (format "%s: %s" date-time type)))
           (str/join \newline)
           (println))
      (let [dest-dir (doto (-> "../data/exist-db" fs/file fs/absolute fs/normalized)
                       (fs/delete-dir)
                       (fs/mkdirs))]
        (doseq [backup backups
                dir ["db/system/config/db/dwdswb"
                     "db/system/security"
                     "db/dwdswb/data"]
                :let [source-base (str export-dir "/" (backup :sig) "/" dir)
                      dest-base (fs/file dest-dir (backup :sig) dir)]]
          (fs/mkdirs dest-base)
          (rsync-xml-db (str source-base "/") (str dest-base)))))))

(defn -main [& args]
  (try
    (log/configure)
    (apply run args)
    (finally (shutdown-agents))))
