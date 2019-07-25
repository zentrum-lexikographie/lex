(ns zdl-lex-wikimedia.util)

(def ->clean-map
  (comp (partial into {})
        (partial remove (comp nil? second))))

