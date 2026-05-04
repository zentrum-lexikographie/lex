(ns zdl.lex.auth
  (:import
   (java.net Authenticator PasswordAuthentication)))

(defn create-authenticator
  [user password]
  (when (and user password)
    (let [pw-auth (PasswordAuthentication. user (char-array password))]
      (proxy [Authenticator] []
        (getPasswordAuthentication [] pw-auth)))))
