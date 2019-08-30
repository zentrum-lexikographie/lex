(ns zdl-lex-build.chrome-driver
  (:require [me.raynes.fs :as fs]
            [zdl-lex-build.zip :refer [unzip]]
            [clojure.java.io :as io]))

(def major-version "76")

(def version-url
  (partial str "https://chromedriver.storage.googleapis.com/LATEST_RELEASE_"))

(def download-url
  (partial format
           "https://chromedriver.storage.googleapis.com/%s/chromedriver_%s.zip"))

(defn install-driver [target-dir platform]
  (let [version (slurp (version-url major-version))
        url (download-url version (name platform))
        zip-file (fs/temp-file "chrome-driver." ".zip")
        driver-file (condp = platform :win32 "chromedriver.exe" "chromedriver")]
    (try
      (io/copy (io/input-stream url) zip-file)
      (unzip zip-file target-dir)
      (fs/file target-dir driver-file)
      (finally (fs/delete zip-file)))))

(defn -main [& args]
  (let [driver-base (-> "../chrome-driver" fs/file fs/absolute fs/normalized)]
    (fs/mkdirs driver-base)
    (install-driver driver-base :win32)
    (install-driver driver-base :linux64)))
