(ns zdl.lex.server.html
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
   [clojure.java.io :as io]
   [lambdaisland.hiccup :as h]
   [lambdaisland.ornament :as o :refer [defstyled]])
  (:import
   (com.vladsch.flexmark.html HtmlRenderer)
   (com.vladsch.flexmark.parser Parser)))

(o/set-tokens! {:tw-version 3
                :fonts      {:sans  "PT Sans,sans-serif"
                             :serif "PT Serif,serif"}
                :colors     {:zdl "002bff"}})

(defstyled header :header
  :relative
  [:nav
   :flex :justify-between :items-center :px-4 :py-4 :bg-zdl
   :justify-start :space-x-10
   [:#logo
    [:a :flex
     [:span :sr-only]
     [:img :h-8 :sm:h-10 :w-auto]]]
   [:#menu :flex-1 :flex :items-center :justify-between
    [:nav :flex :space-x-10]
    [:a :text-white :hover:underline
     [:.title :text-lg :text-white :font-black]]]]
  ([ctx-path]
   [:nav
    [:div#logo
     [:a {:href ctx-path}
      [:span "ZDL – Zentrum für digitale Lexikographie der deutschen Sprache"]
      [:img {:src (str ctx-path "assets/logo-zdl.svg")}]]]
    [:div#menu
     [:a {:href ctx-path :title "Lexikographische Arbeitsumgebung"}
      [:span.title "Lexikographische Arbeitsumgebung"]]]]))

(def logos
  [["Berlin-Brandenburgische Akademie der Wissenschaften"
    "https://www.bbaw.de/"
    "logo-bbaw.png"]
   ["Niedersächsische Akademie der Wissenschaften zu Göttingen"
    "https://adw-goe.de/"
    "logo-nadwg.png"]
   ["Akademie der Wissenschaften und der Literatur | Mainz"
    "https://www.adwmainz.de/"
    "logo-adwl-mainz.png"]
   ["Sächsische Akademie der Wissenschaften"
    "https://www.saw-leipzig.de/"
    "logo-saw.png"]
   ["Leibniz-Institut für Deutsche Sprache Mannheim"
    "https://www.ids-mannheim.de/"
    "logo-ids.png"]
   ["Union der deutschen Akademien der Wissenschaften"
    "https://www.akademienunion.de/"
    "logo-akademienunion.png"]])

(defstyled footer :footer
  [:.logos :px-4 :sm:px-6 :lg:px-8 :py-16
   [:ul :max-w-7xl :mx-auto :grid :grid-cols-2 :md:grid-cols-3 :lg:grid-cols-3 :gap-8
    [:li :cols-span-1 :flex :justify-center
     [:img :object-scale-down :h-30]]]]
  [:p :py-8 :text-center :text-base :text-gray-400]
  ([ctx-path]
   [:<>
    [:div.logos
     [:ul
      (for [[title href img] logos]
        [:li
         [:a {:href href :title title}
          [:img {:src (str ctx-path "assets/" img) :alt title}]]])]]
    [:p
     [:strong "Nutzungsbedingungen: "]
     "Alle Rechte vorbehalten. Nur für die projektinterne Nutzung."]]))

(defstyled page :html
  :h-full :bg-white :font-sans
  [:body :h-full :font-sans
   [:main :max-w-5xl :mx-auto :py-12 :sm:py-16 :px-4 :sm:px-6 :lg:px-8]]
  ([ctx-path title & contents]
   [:<> {:lang "de"}
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
     [:link {:rel "stylesheet" :href (str ctx-path "assets/fonts.css")}]
     [:link {:rel "stylesheet" :href (str ctx-path "styles.css")}]
     [:title (str title " :: ZDL – Lexikographische Arbeitsumgebung")]]
    [:body
     [header ctx-path]
     [:main contents]
     [footer ctx-path]]]))

(def md-parser
  (.. (Parser/builder) (build)))

(def md-renderer
  (.. (HtmlRenderer/builder) (build)))

(defn render-md
  [s]
  (.render md-renderer (.parse md-parser s)))

(defstyled md :div
  ([k]
   [::h/unsafe-html (-> (str "public/" k ".md") io/resource slurp render-md)]))

(defstyled install-help :div
  :max-w-5xl :mx-auto :py-12 :px-4 :sm:px-6 :lg:px-8
  [:h1 :text-3xl :text-gray-900 :font-serif :font-extrabold]
  [:h3 :text-xl :text-gray-900 :font-bold]
  [:a :text-zdl :hover:underline]
  [:p :mt-2 :text-lg :text-justify]
  [:p.subtitle :mt-2 :text-lg :text-gray-500]
  [:ol.instructions :divide-y :divide-gray-200
   [:li :pt-6 :pb-8 :md:grid :md:grid-cols-12 :md:gap-8
    [:div.text :mt-2 :md:mt-0 :md:col-span-6]
    [:div.img :md:col-span-6
     [:img :mx-auto :my-4 :rounded-3xl :w-fit #_:md:w-80 :aspect-square]]]]
  ([ctx-path]
   [:<>
    [:h1 "Installationsanleitung für die lexikographische Arbeitsumgebung"]
    [:p.subtitle
     "Wie installiere ich die Erweiterungen des "
     [:a {:href "https://www.oxygenxml.com/" :title "Oxygen XML Editor Homepage"}
      "Oxygen XML Editors"]
     ", um lexikographische Artikel des DWDS zu bearbeiten"]
    [:ol.instructions
     [:li
      [:div.text [md "install-1"]]
      [:div.img [:img {:src (str ctx-path "assets/install-1.png")}]]]
     [:li
      [:div.text [md "install-2"]]
      [:div.img [:img {:src (str ctx-path "assets/install-2.png")}]]]
     [:li
      [:div.text [md "install-3"]]
      [:div.img [:img {:src (str ctx-path "assets/install-3.png")}]]]
     [:li
      [:div.text [md "install-4"]]
      [:div.img [:img {:src (str ctx-path "assets/install-4.png")}]]]
     [:li
      [:div.text [md "install-5"]]
      [:div.img [:img {:src (str ctx-path "assets/install-5.png")}]]]
     [:li
      [:div.text [md "install-6"]]
      [:div.img [:img {:src (str ctx-path "assets/install-6.png")}]]]]]))

(defn install
  [ctx-path]
  (h/render
   [page ctx-path "Installationsanleitung"
    [install-help ctx-path]]))

(def css
  (o/defined-styles {:preflight? true}))
