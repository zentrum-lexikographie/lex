(ns zdl.lex.server.auth
  (:require
   [clojure.set :as sets]
   [clojure.string :as str]
   [reitit.core :as r]
   [sieppari.context :as sieppari-context]
   [zdl.lex.env :as env])
  (:import
   (java.util Base64)))

;; # HTTP basic authentication middleware for ring.
;;
;; Copyright (c) Remco van 't Veer. All rights reserved. The use and
;; distribution terms for this software are covered by the Eclipse Public
;; License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be
;; found in the file epl-v10.html at the root of this distribution. By using
;; this software in any fashion, you are agreeing to be bound by the terms of
;; this license. You must not remove this notice, or any other, from this
;; software.

(defn- byte-transform
  "Used to encode and decode strings.  Returns nil when an exception
  was raised."
  [direction-fn string]
  (try
    (str/join (map char (direction-fn (.getBytes string))))
    (catch Exception _)))

(defn- encode-base64
  "Will do a base64 encoding of a string and return a string."
  [^String string]
  (byte-transform #(.encode (Base64/getEncoder) %) string))

(defn- decode-base64
  "Will do a base64 decoding of a string and return a string."
  [^String string]
  (byte-transform #(.decode (Base64/getDecoder) %) string))

(defn basic-authentication-request
  "Authenticates the given request against using auth-fn. The value
  returned by auth-fn is assoc'd onto the request as
  :basic-authentication.  Thus, a truthy value of
  :basic-authentication on the returned request indicates successful
  authentication, and a false or nil value indicates authentication
  failure."
  [request auth-fn]
  (let [auth ((:headers request) "authorization")
        cred (and auth (decode-base64 (last (re-find #"^Basic (.*)$" auth))))
        [user pass] (and cred (str/split (str cred) #":" 2))]
    (assoc request :basic-authentication (and cred (auth-fn (str user) (str pass))))))

(defn authentication-failure
  "Returns an authentication failure response, which defaults to a
  plain text \"access denied\" response.  :status and :body can be
  overriden via keys in denied-response, and :headers from
  denied-response are merged into those of the default response.
  realm defaults to \"restricted area\" if not given."
  [& [realm denied-response]]
  (assoc (merge {:status 401
                 :body   "access denied"}
                denied-response)
    :headers (merge {"WWW-Authenticate" (format "Basic realm=\"%s\""
                                                (or realm "restricted area"))
                     "Content-Type"     "text/plain"}
                    (:headers denied-response))))

(defn wrap-basic-authentication
  "Wrap response with a basic authentication challenge as described in
  RFC2617 section 2.

  The authenticate function is called with two parameters, the userid
  and password, and should return a value when the login is valid.  This
  value is added to the request structure with the :basic-authentication
  key.

  The realm is a descriptive string visible to the visitor.  It,
  together with the canonical root URL, defines the protected resource
  on the server.

  The denied-response is a ring response structure which will be
  returned when authorization fails.  The appropriate status and
  authentication headers will be merged into it.  It defaults to plain
  text 'access denied' response."
  [app authenticate & [realm denied-response]]
  (fn [req]
    (let [auth-req (basic-authentication-request req authenticate)]
      (if (:basic-authentication auth-req)
        (app auth-req)
        (authentication-failure realm denied-response)))))

(def auth-user
  (get-in env/config [:zdl.lex.client.http/server :user]))

(def auth-password
  (get-in env/config [:zdl.lex.client.http/server :password]))

(defn authenticate
  [username password]
  (if-not (and auth-user auth-password)
    ;; pass-through credentials from upstream
    {:user username :password password}
    ;; else authenticate against env credentials
    (when (and (= auth-user username) (= auth-password password))
      {:user username :password password})))

(defn authenticated-request
  [request]
  (let [auth-req (basic-authentication-request request authenticate)
        {:keys [user] :as basic-auth} (:basic-authentication auth-req)]
    (cond-> request
      basic-auth (assoc ::identity
                        (assoc basic-auth :roles
                               (cond-> #{:user}
                                 (= "admin" user) (conj :admin)))))))

(defn required-roles
  [{:keys [request-method] ::r/keys [match]}]
  (or (get-in match [:data request-method ::roles])
      (get-in match [:data ::roles])))

(def realm
  "ZDL-Lex-Server")

(defn enter
  [{:keys [request] :as ctx}]
  (let [required (required-roles request)]
    (or
     (and (empty? required) ctx)
     (let [{::keys [identity] :as request} (authenticated-request request)
           {:keys [roles]}                 identity]
       (if (sets/subset? required roles)
         (assoc ctx :request request)
         (sieppari-context/terminate
          (assoc ctx :response (authentication-failure realm))))))))

(def interceptor
  {:name ::interceptor :enter enter})

(defn get-status
  [{::keys [identity]}]
  {:status 200 :body identity})
