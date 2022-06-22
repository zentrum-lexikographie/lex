(ns build-util
  (:require [babashka.fs :as fs]
            [babashka.process :refer [check process]]
            [clojure.string :as str]))

(defn run-proc!
  ([cmd]
   (run-proc! cmd nil))
  ([cmd opts]
   (check (process cmd (assoc opts :inherit true)))))

(defn docker!
  [& args]
  (run-proc! (concat ["docker"] args)))

(defn docker-compose!
  [& args]
  (run-proc! (concat ["docker-compose"] args)))

(defn clojure!
  [& args]
  (run-proc! (concat ["clojure"] args)))

(defn run->str!
  ([cmd]
   (run->str! cmd nil))
  ([cmd opts]
   (with-out-str (run-proc! cmd (assoc opts :out *out*)))))

(defn id
  [& args]
  (str/trim (run->str! (concat ["id"] args))))

(defn current-user
  []
  (str/join ":" [(id "-u") (id "-g")]))

(defn git-rev-count
  []
  (str/trim (run->str! ["git" "rev-list" "HEAD" "--count"])))

(defn current-version
  []
  (let [now       (java.time.OffsetDateTime/now)
        year      (.getYear now)
        month     (.getMonthValue now)
        day       (.getDayOfMonth now)
        rev-count (git-rev-count)]
    (format "%04d%02d.%02d.%s" year month day rev-count)))

(def oxygen-dir
  (fs/file (fs/absolutize "oxygen")))

(def oxygen-local-installs
  (->> (fs/glob "/usr/local" "Oxygen XML Editor*" {:recursive true})
       (map #(.toFile ^java.nio.file.Path %))
       (sort-by fs/file-name #(compare %2 %1))))

(def oxygen-home
  (->>
   (concat [(some-> (System/getenv "OXYGEN_HOME") fs/file)
            (fs/file (System/getProperty "user.home") "oxygen")]
           oxygen-local-installs)
   (remove nil?)
   (filter fs/directory?)
   (first)))
