(ns zdl-lex-wikimedia.util)

(defn ->clean-map [m]
  "A map with nil values removed."
  (->> (remove (comp nil? second) m)
       (into {})))

