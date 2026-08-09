(ns zdl.lex.issue
  (:require
   [clojure.string :as str]
   [gremid.xml :as gx]
   [lambdaisland.uri :as uri]
   [medley.core :refer [distinct-by]]
   [org.httpkit.client :as hc]
   [tick.core :as t]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.index :as index]
   [zdl.lex.metrics :as metrics]))

(def api-url
  "https://mantis.dwds.de/mantis/api/soap/mantisconnect.php")

(def api-soap-action
  (str api-url "/mc_project_get_issues"))

(def api-user
  (getenv "MANTIS_API_USER" "dwdsweb"))

(def api-password
  (getenv "MANTIS_API_PASSWORD"))

(defn xml-property
  [node property]
  (some->> node (gx/element property) (gx/text)))

(defn xml-ref-property
  [node property ref-prop]
  (some->> node (gx/element property) (gx/element ref-prop) (gx/text)))

(defn xml->issue
  [item-xml]
  (let [summary (xml-property item-xml :summary)]
    {:id         (str (uri/->URI "mantis" nil nil nil nil
                                 (xml-property item-xml :id) nil nil))
     :summary    summary
     :form       (some-> summary (str/split #" --") first)
     :updated    (some-> (xml-property item-xml :last_updated)
                         (t/offset-date-time) (t/instant) (str))
     :category   (xml-ref-property item-xml :category :name)
     :status     (xml-ref-property item-xml :status :name)
     :severity   (xml-ref-property item-xml :severity :name)
     :reporter   (xml-ref-property item-xml :reporter :real_name)
     :handler    (xml-ref-property item-xml :handler :real_name)
     :resolution (xml-ref-property item-xml :resolution :name)}))

(defn request-issues
  [page]
  (let [soap-msg (->>
                  [:SOAP-ENV:Envelope
                   {:xmlns:SOAP-ENV         "http://schemas.xmlsoap.org/soap/envelope/"
                    :xmlns:soap             "http://schemas.xmlsoap.org/wsdl/soap/"
                    :xmlns:mantis           "http://futureware.biz/mantisconnect"
                    :xmlns:xsd              "http://www.w3.org/2001/XMLSchema"
                    :xmlns:xsi              "http://www.w3.org/2001/XMLSchema-instance"
                    :SOAP-ENV:encodingStyle "http://schemas.xmlsoap.org/soap/encoding/"}
                   [:SOAP-ENV:Body
                    [:mantis:mc_project_get_issues
                     [:username {:xsi:type "xsd:string"} api-user]
                     [:password {:xsi:type "xsd:string"} api-password]
                     [:project_id {:xsi:type "xsd:integer"} "5"]
                     [:page_number {:xsi:type "xsd:integer"} (str page)]
                     [:per_page {:xsi:type "xsd:integer"} "500"]]]]
                  (gx/sexp->node) (gx/write-node *out*) (with-out-str))
        resp     @(hc/request {:method  :post
                               :url     api-url
                               :headers {"Content-Type" "text/xml"
                                         "SOAPAction"   api-soap-action}
                               :body    soap-msg})]
    (when (resp :error) (throw (resp :error)))
    (when (not= (resp :status) 200) (throw (ex-info "Mantis API error" resp)))
    (some->> resp :body gx/read-events gx/events->node
             (gx/element :return) :content (map xml->issue))))

(defn issues
  ([]
   (distinct-by :id (issues 1)))
  ([page]
   (some-> (request-issues page) (seq) (lazy-cat (issues (inc page))))))

(def sync-timer
  (metrics/timer "mantis.sync"))

(defn sync!
  []
  (with-open [_ (metrics/timed! sync-timer)]
   (let [threshold (System/currentTimeMillis)]
     (transduce
      (comp (filter :form) (map index/issue->doc) (partition-all 10000))
      (completing (fn [n batch] (index/add! batch) (+ n (count batch))))
      0
      (issues))
     (index/purge! "issue" threshold))))
