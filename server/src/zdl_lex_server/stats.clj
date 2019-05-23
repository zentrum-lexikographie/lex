(ns zdl-lex-server.stats
  (:require [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [hiccup.page :refer [html5 include-css]]))

(defn handle [req]
  (-> (htstatus/ok (html5 [:head (include-css "/assets/bulma/css/bulma.min.css")]
                          [:body
                           [:div.hero.is-primary.is-bold.is-fullheight
                            [:div.hero-body.container
                             [:p.title "ZDL Lex-Server"]]]]))
      (assoc :headers {"Content-Type" "text/html"})))

