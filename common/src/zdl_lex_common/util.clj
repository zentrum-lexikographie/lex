(ns zdl-lex-common.util)

(defn ->clean-map [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))
