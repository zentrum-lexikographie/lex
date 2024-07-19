(ns build
  (:require
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]
   [clojure.tools.build.tasks.copy :as copy]
   [gremid.data.xml.rnc :as dx.rnc]
   [gremid.data.xml.schematron :as dx.schematron]
   [taoensso.timbre :as log]
   [clojure.string :as str])
  (:import
   (java.lang ProcessBuilder$Redirect)))

;; ## Process handling

(defn check!
  [^Process proc]
  (.waitFor ^Process proc)
  (let [exit-status (.exitValue proc)]
    (when-not (zero? exit-status)
      (throw (ex-info (str "Error executing command: " exit-status)
                      {:proc proc})))))

(defn proc!
  ([cmd]
   (proc! cmd "."))
  ([cmd dir]
   (log/debugf "[@ %s] ! %s" dir cmd)
   (.. (ProcessBuilder. (into-array String cmd))
       (directory (io/file dir))
       (redirectInput ProcessBuilder$Redirect/INHERIT)
       (redirectOutput ProcessBuilder$Redirect/INHERIT)
       (redirectError ProcessBuilder$Redirect/INHERIT)
       (start))))

(def check-proc!
  (comp check! proc!))

(defn proc->str!
  [cmd]
  (let [proc   (.. (ProcessBuilder. (into-array String cmd))
                   (redirectInput ProcessBuilder$Redirect/INHERIT)
                   (redirectError ProcessBuilder$Redirect/INHERIT)
                   (start))
        result (future
                 (with-open [r (io/reader (.getInputStream ^Process proc))]
                   (slurp r)))]
    (check! proc)
    @result))



(def oxygen-dir
  (.getAbsolutePath (io/file "oxygen")))

(def oxygen-local-installs
  (->> (.listFiles (io/file "/usr/local"))
       (filter #(.. % (getName) (startsWith "Oxygen XML Editor")))
       (sort-by #(.getName %) #(compare %2 %1))))

(def oxygen-home
  (->>
   (concat [(some-> (System/getenv "OXYGEN_HOME") io/file)
            (io/file (System/getProperty "user.home") "oxygen")]
           oxygen-local-installs)
   (remove nil?)
   (filter #(.isDirectory %))
   (first)))

(defn id
  [& args]
  (str/trim (proc->str! (concat ["id"] args))))

(defn current-user
  []
  (str/join ":" [(id "-u") (id "-g")]))

(defn git-rev-count
  []
  (str/trim (proc->str! ["git" "rev-list" "HEAD" "--count"])))

(defn current-version
  []
  (let [now       (java.time.OffsetDateTime/now)
        year      (.getYear now)
        month     (.getMonthValue now)
        day       (.getDayOfMonth now)
        rev-count (git-rev-count)]
    (format "%04d%02d.%02d.%s" year month day rev-count)))

;; ## Tasks

(defn write-version
  [& _]
  (let [version (current-version)]
    (log/infof "Stamping version: %s" version)
    (spit (io/file "src" "version.edn") (pr-str {:version version}))))

(defn build-schema
  [& _]
  (log/info "Transpiling DWDSwb schema")
  (let [framework-dir (io/file "oxygen" "framework")
        rnc           (io/file framework-dir "rnc" "DWDSWB.rnc")
        rng           (io/file framework-dir "rng" "DWDSWB.rng")
        sch           (io/file framework-dir "rng" "DWDSWB.sch.xsl")]
    (doseq [f [rnc rng sch]] (io/make-parents f))
    (dx.rnc/->rng rnc rng)
    (dx.schematron/extract-xslt rng sch)))

(def client-ignores
  (conj copy/default-ignores
        "^\\.env$"
        "^plugin/.*"
        "^plugin\\.dtd"
        "^project\\.xpr"))

(defn build-client
  [& _]
  (write-version)
  (build-schema)
  (log/info "Compiling Oxygen XML Editor plugin")
  (let [compile-basis (b/create-basis {:project "deps.edn"
                                       :aliases #{:client :oxygen :log}})
        package-basis (b/create-basis {:project "deps.edn"
                                       :aliases #{:client}})
        classes-dir   "classes/client"
        jar-file      "oxygen/plugin/lib/org.zdl.lex.client.jar"]
    (b/delete {:path jar-file})
    (b/delete {:path classes-dir})
    (b/copy-dir {:src-dirs   ["src" "oxygen"]
                 :target-dir classes-dir
                 :ignores    client-ignores})
    (b/compile-clj {:basis        compile-basis
                    :src-dirs     ["src"]
                    :ns-compile   ['zdl.lex.client.io
                                   'zdl.lex.client.oxygen
                                   'zdl.lex.client.plugin]
                    :compile-opts {:disable-locals-clearing true
                                   :elide-meta              []
                                   :direct-linking          false}
                    :class-dir    classes-dir})
    (b/uber {:class-dir classes-dir
             :uber-file jar-file
             :basis     package-basis})))

(def server-ignores
  (conj client-ignores "^\\.clj$"))

(defn build-server
  [& _]
  (build-client)
  (log/info "Compiling server")
  (let [basis       (b/create-basis {:project "deps.edn"
                                     :aliases #{:server :log}})
        classes-dir "classes/server"
        jar-file    "org.zdl.lex.server.jar"]
    (b/delete {:path jar-file})
    (b/delete {:path classes-dir})
    (b/copy-dir {:src-dirs   ["src" "oxygen"]
                 :target-dir classes-dir
                 :ignores    server-ignores})
    (b/compile-clj {:basis      basis
                    :src-dirs   ["src"]
                    :ns-compile ['zdl.lex.server]
                    :class-dir  classes-dir})
    (b/uber {:class-dir classes-dir
             :uber-file jar-file
             :main      'zdl.lex.server
             :basis     basis})))

(defn package
  [& _]
  (log/info "Packaging server")
  (check-proc! ["docker" "compose" "build" "solr" "server"]))

(defn image-tag
  ([image-name]
   (image-tag image-name (current-version)))
  ([image-name image-version]
   (str "docker.zdl.org/zdl-lex/" image-name ":" image-version)))

(defn release
  [& _]
  (package)
  (log/info "Releasing server (including client)")
  (doseq [service ["solr" "server"]]
    (let [version-tag (image-tag service)
          latest-tag  (image-tag service "latest")]
      (check-proc! ["docker" "tag" latest-tag version-tag])
      (check-proc! ["docker" "push" version-tag]))))

(defn start-editor
  [& _]
  (when-not oxygen-home
    (throw (ex-info "$OXYGEN_HOME not found" {})))
  (log/info "Starting client")
  (check-proc!
   ["java"
    "--add-opens=java.base/java.lang=ALL-UNNAMED"
    "--add-opens=java.base/java.net=ALL-UNNAMED"
    "--add-opens=java.base/java.util=ALL-UNNAMED"
    "--add-opens=java.base/java.util.regex=ALL-UNNAMED"
    "--add-opens=java.base/sun.net.util=ALL-UNNAMED"
    "--add-opens=java.base/sun.net.www.protocol.http=ALL-UNNAMED"
    "--add-opens=java.base/sun.net.www.protocol.https=ALL-UNNAMED"
    "--add-opens=java.desktop/java.awt=ALL-UNNAMED"
    "--add-opens=java.desktop/java.awt.dnd=ALL-UNNAMED"
    "--add-opens=java.desktop/javax.swing=ALL-UNNAMED"
    "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED"
    "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED"
    "--add-opens=java.xml/com.sun.org.apache.xerces.internal.xni=ALL-UNNAMED"
    "--add-opens=javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED"
    "--add-opens=javafx.web/javafx.scene.web=ALL-UNNAMED"
    "-Xmx1g"
    "-XX:-OmitStackTraceInFastThrow"
    "-XX:SoftRefLRUPolicyMSPerMB=10"
    "-Dcom.oxygenxml.editor.plugins.dir=."
    "-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor"
    "-Djava.net.preferIPv4Stack=true"
    "-Dsun.io.useCanonCaches=true"
    "-Dsun.io.useCanonPrefixCache=true"
    "-cp" (->>
           [(io/file "lib")
            (io/file oxygen-home "classes")
            (io/file oxygen-home "lib" "oxygen-basic-utilities.jar")
            (io/file oxygen-home "lib" "oxygen.jar")
            (io/file oxygen-home)]
           (str/join \:))
    "ro.sync.exml.Oxygen"
    "project.xpr"]
   oxygen-dir))

(defn editor
  [& _]
  (build-client)
  (start-editor))

(def gpt-model-dir
  (io/file "models"))

(def gpt-model-url
  (str "https://huggingface.co/"
       "TheBloke/DiscoLM_German_7b_v1-GGUF/resolve/main/"
       "discolm_german_7b_v1.Q4_K_M.gguf"))

(defn download-gpt-model
  [& _]
  (check-proc! ["curl" "-LO" gpt-model-url] gpt-model-dir))
