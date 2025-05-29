(ns build
  (:require
   [babashka.fs :as fs]
   [clojure.java.process :as p]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [clojure.tools.build.tasks.copy :as copy]
   [gremid.xml-schema :as gxs]
   [taoensso.telemere :as tm]
   [tick.core :as t]))

(def oxygen-dir
  (fs/absolutize "oxygen"))

(def oxygen-local-installs
  (->> (fs/list-dir "/usr/local")
       (filter #(str/starts-with? (fs/file-name %) "Oxygen XML Editor"))
       (sort-by fs/file-name #(compare %2 %1))))

(def oxygen-home-install
  (fs/path (System/getProperty "user.home") "oxygen"))

(def oxygen-home-env
  (some-> (System/getenv "OXYGEN_HOME") fs/file))

(def oxygen-home
  (->>
   (cons oxygen-home-install oxygen-local-installs)
   (cons oxygen-home-env) (remove nil?) (filter fs/directory?) (first)))

(def current-timestamp
  (t/format "yyyyMM.dd.HHmm" (t/in (t/now) "UTC")))

(defn timestamp
  [& _]
  (tm/log! :info (format "Timestamp: %s" current-timestamp))
  (spit (fs/file "oxygen" "VERSION") current-timestamp))

(defn schema
  [& _]
  (tm/log! :info "Transpiling DWDSwb schema")
  (let [framework-dir (fs/file "oxygen" "framework")
        rnc           (fs/file framework-dir "rnc" "DWDSWB.rnc")
        rng           (fs/file framework-dir "rng" "DWDSWB.rng")
        sch           (fs/file framework-dir "rng" "DWDSWB.sch.xsl")]
    (doseq [f [rnc rng sch]] (-> f fs/parent fs/create-dirs))
    (gxs/rnc->rng (str rnc) (str rng))
    (gxs/rng->sch-xsl rng sch)))

(def client-ignores
  (conj copy/default-ignores
        "^\\.env$"
        "^plugin/.*"
        "^plugin\\.dtd"
        "^project\\.xpr"))

(defn client
  [& _]
  (schema)
  (tm/log! :info "Compiling Oxygen XML Editor plugin")
  (let [compile-basis (b/create-basis {:project "deps.edn"
                                       :aliases #{:client :oxygen :classes}})
        package-basis (b/create-basis {:project "deps.edn"
                                       :aliases #{:client :classes}})
        classes-dir   "classes"
        jar-file      "oxygen/plugin/lib/org.zdl.lex.client.jar"]
    (b/delete {:path jar-file})
    (b/delete {:path classes-dir})
    (b/copy-dir {:src-dirs   ["src"]
                 :target-dir classes-dir
                 :ignores    client-ignores})
    (b/compile-clj {:basis        compile-basis
                    :src-dirs     ["src"]
                    :ns-compile   ['zdl.lex.oxygen.extension
                                   'zdl.lex.oxygen.plugin
                                   'zdl.lex.oxygen.url-handler]
                    :compile-opts {:disable-locals-clearing true
                                   :elide-meta              []
                                   :direct-linking          false}
                    :class-dir    classes-dir})
    (b/uber {:class-dir classes-dir
             :uber-file jar-file
             :basis     package-basis})
    (b/delete {:path classes-dir})))

(defn editor
  [& _]
  (assert oxygen-home "$OXYGEN_HOME not found")
  (tm/log! :info "Starting client")
  (p/exec
   {:dir    (fs/file oxygen-dir)
    :stdout :inherit
    :stderr :inherit}
   (str (fs/file oxygen-home "jre" "bin" "java"))
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
          [(fs/file oxygen-home "classes")
           (fs/file oxygen-home "lib" "oxygen-basic-utilities.jar")
           (fs/file oxygen-home "lib" "oxygen.jar")
           (fs/file oxygen-home)]
          (str/join \:))
   "ro.sync.exml.Oxygen"
   "project.xpr"))

(defn client+editor
  [& _]
  (client)
  (editor))

(defn server
  [& _]
  (timestamp)
  (schema))
