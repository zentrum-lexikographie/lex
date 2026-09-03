(ns zdl.lex.metrics
  (:require
   [iapetos.collector.jvm :as prometheus.jvm]
   [iapetos.collector.ring :as prometheus.ring]
   [iapetos.core :as prometheus]))

(def registry
  (-> (prometheus/collector-registry)
      (prometheus.jvm/initialize)
      (prometheus.ring/initialize)
      (prometheus/register
       (prometheus/counter :zdl_lex/error {:labels [:source]})
       (prometheus/histogram :zdl_lex/git {:labels [:action]})
       (prometheus/histogram :zdl_lex/gpt)
       (prometheus/histogram :zdl_lex/index {:labels [:action]})
       (prometheus/histogram :zdl_lex/korap {:labels [:corpus]})
       (prometheus/histogram :zdl_lex/mantis {:labels [:action]}))))
