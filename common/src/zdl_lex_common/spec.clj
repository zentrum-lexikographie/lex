(ns zdl-lex-common.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(s/def ::pos-int (s/with-gen
                   (s/and int? (partial <= 0))
                   #(gen/large-integer* {:min 0})))
