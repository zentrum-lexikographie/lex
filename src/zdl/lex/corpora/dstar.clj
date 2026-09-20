(ns zdl.lex.corpora.dstar
  (:require
   [zdl.lex.env :refer [getenv]]
   [org.httpkit.client :as hc]
   [jsonista.core :as json]
   [taoensso.telemere :as tel])
  (:import
   (java.io DataInputStream OutputStream)
   (java.net Socket)
   (java.nio ByteBuffer ByteOrder)
   (java.nio.charset Charset)))

(def auth
  (let [user     (getenv "DDC_DSTAR_USER")
        password (getenv "DDC_DSTAR_PASSWORD")]
    (when (and user password) [user password])))

(def index-url
  (cond-> "https://ddc.dwds.de/dstar/" auth (str "intern.perl")))

(defn corpora
  []
  (->>
   (-> {:method       :get
        :basic-auth   auth
        :url          index-url
        :query-params {"f" "json"}}
       (hc/request) (deref) (get :body) (json/read-value))
   (into (sorted-map)
         (map (fn [{k "corpus" host "host" port "port"}] [k [host (parse-long port)]])))))

(def ^Charset charset
  (Charset/forName "UTF-8"))

(defn ddc
  [[host port :as endpoint] cmd]
  (tel/with-ctx+ {::host host ::port port ::cmd cmd}
    (try
      (tel/event! ::ddc :debug)
      (with-open [socket (Socket. ^String host (int port))
                  output (.getOutputStream socket)
                  input  (DataInputStream. (.getInputStream socket))]
        (let [request     (. ^String cmd (getBytes charset))
              request-len (count request)]
          (. output (write (.. (ByteBuffer/allocate 4)
                               (order ByteOrder/LITTLE_ENDIAN)
                               (putInt (unchecked-int request-len))
                               (array))))
          (. output (write request))
          (. output (flush))
          (let [response-len (byte-array 4)
                _            (.readFully input response-len)
                response-len (.. (ByteBuffer/wrap response-len)
                                 (order ByteOrder/LITTLE_ENDIAN)
                                 (getInt))
                response     (byte-array response-len)]
            (.readFully input response)
            (json/read-value (String. response charset)))))
      (catch Throwable t
        (throw (tel/error! ::ddc (ex-info "DDC request error"
                                          {:endpoint endpoint :cmd cmd} t)))))))
