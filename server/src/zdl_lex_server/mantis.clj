(ns zdl-lex-server.mantis
  (:require [clj-soap.client :as soap]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store]))

(def client
  (let [mantis-base (config :mantis-url)
        wsdl (str mantis-base "/api/soap/mantisconnect.wsdl")
        endpoint (str mantis-base "/api/soap/mantisconnect.php")]
    (soap/client-fn {:wsdl wsdl :options {:endpoint-url endpoint}})))

(def ^:private authenticate
  (partial merge {:username (config :mantis-user)
                  :password (config :mantis-password)}))

(def ^:private project (config :mantis-project))

(defn- zip->items [loc]
  (zx/xml-> loc dz/children zip/branch? :return :item))

(defn- results
  ([op] (results op {}))
  ([op params] (results op params zip->items))
  ([op params zip->locs]
   (timbre/info {:op op :params (dissoc params :username :password)})
   (-> (client op (authenticate params))
       (zip/xml-zip)
       (zip->locs))))

(defn scroll
  ([op] (scroll op {}))
  ([op params] (scroll op params zip->items))
  ([op params zip->locs] (scroll op params zip->locs 1000))
  ([op params zip->locs page-size] (scroll op params zip->locs page-size 0))
  ([op params zip->locs page-size page]
   (let [params (merge params {:page_number page :per_page page-size})
         results (seq (results op params zip->locs))]
     (if results
       (concat results
               (lazy-seq (scroll op params zip->locs page-size (inc page))))))))

(defn- property [loc k]
  (zx/xml1-> loc k zx/text))

(defn mantis-enum
  ([op] (mantis-enum op {}))
  ([op params]
   (into {}
         (for [item (results op params)]
           (vector (property item :id)
                   (property item :name))))))

(defn issues []
  (let [status (mantis-enum :mc_enum_status)
        severities (mantis-enum :mc_enum_severities)
        resolutions (mantis-enum :mc_enum_resolutions)
        users (mantis-enum :mc_project_get_users {:project_id project :access 0})]
    (->> (scroll :mc_project_get_issue_headers {:project_id project})
         (map (fn [item]
                {:id (-> (property item :id) Integer/parseInt)
                 :category (property item :category)
                 :summary (property item :summary)
                 :last-updated (-> (property item :last_updated) t/parse)
                 :status (-> (property item :status) status)
                 :severity (-> (property item :severity) severities)
                 :reporter (-> (property item :reporter) users)
                 :handler (-> (property item :handler) users)
                 :resolution (-> (property item :resolution) resolutions)
                 :attachments (-> (property item :attachments_count) Integer/parseInt)
                 :notes (-> (property item :notes_count) Integer/parseInt)})))))

(comment
  store/mantis-dump
  (time (spit store/mantis-dump (pr-str (vec (issues)))))
  (take 10 (issues)))
