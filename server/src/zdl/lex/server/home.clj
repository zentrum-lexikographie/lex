(ns zdl.lex.server.home
  (:require [hiccup.page :refer [html5 include-css]]
            [ring.util.http-response :as htstatus]))

(defn handle-home [req]
  (-> (html5 [:head (include-css "/assets/bulma/css/bulma.min.css")]
             [:body
              [:div.hero.is-primary.is-bold.is-fullheight
               [:div.hero-body
                [:div.container 
                 [:h1.title "ZDL Lex-Server"]
                 [:h2.subtitle
                  [:a
                   {:href "/oxygen/updateSite.xml"
                    :title "Oxygen XML Editor - Update Site"}
                   "Oxygen XML Editor - Update Site"]]]]]])
      (htstatus/ok)
      (assoc :headers {"Content-Type" "text/html"})))


(def ring-handlers
  [""
   ["/" (fn [_] (htstatus/temporary-redirect "/home"))]
   ["/home" handle-home]])
