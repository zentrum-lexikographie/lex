(ns zdl.lex.ui.examples
  (:require [clojure.string :as str]
            [seesaw.swingx :as uix]))

(def examples
  [] #_(pg/execute env/db
                   (str "select n, txt, gdex, doc, bibl, ex_year from example "
                        "where req_id = 6 "
                        "order by embedding <=> "
                        "(select avg(embedding) from example where req_id = 6 and n in (3006))")))

(defn txt->html
  [s]
  (str "<html>"
       (-> s
           (str/replace #"<t>" "<b>")
           (str/replace #"</t>" "</b>")
           (str/replace #"<c>" "<u>")
           (str/replace #"</c>" "</u>"))
       "</html>"))

(defn gdex->html
  [gdex]
  (let [stars (condp > gdex 0.5 0 0.6 1 0.8 2 3)]
    (str
     "<html><font color=\"#AB8000\">"
     (str/join (concat (repeat stars \★) (repeat (- 3 stars) \☆)))
     "</font></html>")))

(def table
  (doto (uix/table-x
         :model [:columns [:txt :year :gdex :n]
                 :horizontal-scroll-enabled? true
                 :rows    (->> examples
                               (into [] (map (fn [{:keys [n gdex ex_year txt]}]
                                               [(txt->html txt) ex_year (gdex->html gdex) n]))))])
    (.setShowGrid false)
    (.setSortable false)))

#_(comment
  (pg/execute env/db "select ex_year, count(*) from example where req_id = 6 group by ex_year order by ex_year")
  (frequencies (map (comp count (partial re-seq #"<t>") :txt) examples)))
