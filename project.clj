(defproject org.zdl.lex/lex :lein-v
  :plugins [[lein-modules "0.3.11"]
            [com.roomkey/lein-v "7.1.0"]]

  :profiles {:dev [:project/dev :profiles/dev]

             :project/dev
             {:dependencies [[midje "1.9.8"]]}

             :inherited
             {:dependencies
              [[org.clojure/clojure "1.10.1"]
               [org.clojure/core.async "0.4.490"]
               [org.slf4j/slf4j-api "_"]
               [org.slf4j/jul-to-slf4j "_"]
               [org.slf4j/jcl-over-slf4j "_"]
               [com.taoensso/timbre "4.10.0"]
               [com.fzakaria/slf4j-timbre "0.3.12"]
               [me.raynes/fs "1.4.6"]
               [metosin/spec-tools "0.10.0"]
               [environ "1.1.0"]
               [mount "0.1.16"]
               [tick "0.4.10-alpha"
                :exclusions [cljsjs/js-joda-locale-en-us
                             cljsjs/js-joda-timezone]]]

              :description "Lexikographic Workbench of the ZDL"
              :url "https://zdl.org/"

              :license {:name "LGPL-3.0"
                        :url "https://www.gnu.org/licenses/lgpl-3.0.html"}

              :middleware [leiningen.v/add-workspace-data]

              :prep-tasks [["v" "cache" "src" "edn"] "javac" "compile"]

              :plugins [[lein-environ "1.1.0"]
                        [com.roomkey/lein-v "7.1.0"]]}}

  :modules {:versions {org.slf4j "1.7.25"
                       org.relaxng "20181222"
                       org.zdl.lex/common "000000.00.00"
                       org.zdl.lex/corpus "000000.00.00"}
            :dirs ["common" "build" "client" "corpus" "wikimedia" "server"]})

