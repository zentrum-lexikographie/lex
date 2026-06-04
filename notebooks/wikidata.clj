(ns wikidata
  (:require
   [clojure.string :as str]
   [julesratte.client :as jr]
   [tick.core :as t]))

;; ## DWDSBot Contributions

(->>
 (jr/requests! {:list        "usercontribs"
                :ucuser      "DwdsBot"
                :ucnamespace "146"
                :uclimit     "500"} 1000)
 (mapcat #(get-in % [:body :query :usercontribs]))
 (map #(select-keys % [:title :timestamp :comment]))
 (remove (comp #(str/includes? % "Sample Form Import") :comment))
 (map #(update % :title str/replace #"^Lexeme:" ""))
 (map #(update % :timestamp t/offset-date-time))
 (map (fn [{:keys [comment] :as edit}]
        (assoc edit :category (condp #(str/includes? %2 %1) comment
                                "Property:P5185"             :genus
                                "Form Import"                :forms
                                "wbeditentity-create-lexeme" :lexeme
                                :other))))
 #_(remove (comp #{:other} :category))
 (vec))
