(ns zdl-lex-client.chrome-driver
  (:require [me.raynes.fs :as fs]
            [me.raynes.fs.compression :as compress]
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
      (compress/unzip zip-file target-dir)
      (fs/file target-dir driver-file)
      (finally (fs/delete zip-file)))))

(comment (install-driver "/home/middell" :win32))
