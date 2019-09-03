(ns zdl-lex-build.exist
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [tick.alpha.api :as t]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as timbre]))

(defn sh->out [& args]
  (timbre/info args)
  (let [{:keys [exit out] :as result} (apply shell/sh args)]
    (when-not (= 0 exit) (throw (ex-info (pr-str args) result)))
    out))

(defn ssh-xml-db [& args]
  (apply sh->out (concat ["ssh" "spock.dwds.de"] args)))

(defn rsync-xml-db [source dest]
  (sh->out "rsync" "-az" (str "spock.dwds.de:" source) dest))

(defn parse-backup-sig [sig]
  (if-let [sig (re-find #"((?:inc)|(?:full))(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})" sig)]
    (let [[sig type year month day hour minute] sig]
      {:sig sig
       :type type
       :date-time 
       (t/at (t/new-date (Integer/parseInt year)
                         (Integer/parseInt month)
                         (Integer/parseInt day))
             (t/new-time (Integer/parseInt hour)
                         (Integer/parseInt minute)))})))


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

(defn -main [& args]
  (try
    (let [dest-dir (doto (-> "../data/exist-db" fs/file fs/absolute fs/normalized)
                     (fs/delete-dir)
                     (fs/mkdirs))]
      (doseq [backup (get-active-backup-chain)
              :let [source-base (str export-dir "/" (backup :sig))]
              dir ["db/system/config/db/dwdswb"
                   "db/system/security"
                   "db/dwdswb/data"]
              :let [dest-base (fs/file dest-dir dir)]]
        (fs/mkdirs dest-base)
        (rsync-xml-db (str source-base "/" dir "/") (str dest-base))))
    (finally (shutdown-agents))))
