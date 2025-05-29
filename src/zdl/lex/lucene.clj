(ns zdl.lex.lucene
  "Handling of Apache Lucene (Solr) Standard Query syntax"
  (:require
   [clojure.string :as str]
   [strojure.parsesso.char :as char]
   [strojure.parsesso.parser :as p]
   [gremid.xml :as gx]))

;; ## Escaping of special chars in regexps, terms etc.

(defn escape
  [char-re s]
  (str/replace s char-re #(str "\\" %)))

(defn unescape
  [char-re]
  (let [re (re-pattern (str "\\\\" char-re))]
    (fn [s] (str/replace s re #(subs % 1 2)))))

(def escape-phrase
  (partial escape #"\""))

(def unescape-phrase
  (unescape "\""))

(def escape-regexp
  (partial escape #"/"))

(def unescape-regexp
  (unescape "/"))

(def escape-pattern
  (partial escape #"[\!\(\)\:\^\[\]\"\{\}\~\\]"))

(def unescape-pattern
  (unescape "[\\!\\(\\)\\:\\^\\[\\]\\\"\\{\\}\\~\\\\]"))

(def escape-term
  (comp (partial escape #"[\*\?]") escape-pattern))

(def unescape-term
  (comp unescape-pattern (unescape "[\\*\\?]")))

;; ## Parsesso-based parser

(defn node
  [tag & content]
  {:tag     tag
   :content content})


(def whitespace-chars
  " \t\n\r\u3000")

(def modifier-chars
  "+-!")

(def field-chars
  ":")

(def range-chars
  "[]{}")

(def subquery-chars
  "()")

(def phrase-chars
  "\"")

(def pattern-chars
  "*?")

(def regex-chars
  "/")

(def escape-chars
  "\\")

(def boost-chars
  "^")

(def fuzzy-chars
  "~")

(def *ws
  (p/*many (char/is whitespace-chars)))


(def +ws
  (p/+many (char/is whitespace-chars)))


(def escaped
  (-> (p/group (char/is escape-chars) (char/is-not whitespace-chars))
      (p/value char/str*)))


(def all
  (p/word "*"))

(def +number
  (p/+many char/number?))


(def number
  (->
   (p/group +number (p/option (p/group (char/is ".") +number)))
   (p/value char/str*)))


(def boost
  (->
   (p/group (p/word "^") number)
   (p/value char/str*)))


(def fuzzy
  (->
   (p/group (p/word "~") (p/option number))
   (p/value char/str*)))


(def not-term-rest-chars
  (str whitespace-chars subquery-chars field-chars
       range-chars phrase-chars fuzzy-chars boost-chars
       pattern-chars phrase-chars escape-chars))

(def not-term-start-chars
  (str not-term-rest-chars modifier-chars regex-chars))


(def term-start
  (p/alt (char/is-not not-term-start-chars) escaped))


(def term-rest
  (p/alt (char/is-not not-term-rest-chars) escaped))


(def term
  (-> (p/group term-start (p/*many term-rest))
      (p/value char/str*)))

(def pattern-start
  (p/alt term-start (char/is pattern-chars)))


(def pattern-rest
  (p/alt term-rest (char/is pattern-chars)))

(def pattern
  (-> (p/group pattern-start (p/*many pattern-rest))
      (p/value char/str* (partial node :match))))

(def phrase
  (->
   (p/*many (p/alt escaped (char/is-not phrase-chars)))
   (p/between (char/is phrase-chars))
   (p/value char/str* (partial node :phr))))


(def regex
  (->
    (p/*many (p/alt escaped (char/is-not regex-chars)))
    (p/between (char/is regex-chars))
    (p/value char/str* (partial node :re))))

(def value
  (p/alt phrase regex number pattern all term))

(def value-query
  (-> (p/group value (p/*many (p/alt fuzzy boost)))
      (p/value (fn [[v opts]]
                 (cond-> (node :v v)
                   (seq opts) (assoc-in [:attrs :opts] opts))))))

(def range-bound
  (p/alt phrase term number all))

(def range-bound-ch->attr
  {\{ "exclusive"
   \} "exclusive"
   \[ "inclusive"
   \] "inclusive"})

(def range-query
  (->
   (p/group
    (char/is "[{")
    *ws range-bound +ws
    (p/word "TO")
    +ws range-bound *ws
    (char/is "}]")
    (p/option boost))
   (p/value
    (fn [[start _ from _ _ _ to _ end boost]]
      (cond-> {:tag     :range
               :attrs   {:start (range-bound-ch->attr start)
                         :end   (range-bound-ch->attr end)}
               :content (list from {:tag :to} to)}
        boost (assoc-in [:attrs :opts] (list boost)))))))

(declare query)

(def subquery
  (p/do-parser
   (->
    (p/between query (char/is "(") (char/is ")"))
    (p/group (p/option boost))
    (p/value (fn [[sq boost]]
               (cond-> (node :sub sq)
                 boost (assoc-in [:attrs :opts] (list boost))))))))

(def field
  (->
   (p/maybe (p/group (p/alt all term) (p/word ":")))
   (p/value (fn [[k _]] (node :field k)))))

(def modifier->tag
  {\! :not
   \+ :must
   \- :must-not})

(def modifier
  (-> (char/is modifier-chars) (p/value modifier->tag)))

(def clause
  (->
   (p/group (p/option modifier) *ws (p/option field)
            (p/alt range-query subquery value-query))
   (p/value (fn [[mod _ field query]]
              (if (and (nil? mod) (nil? field))
                query
                {:tag     :c
                 :content (cond->> (list query)
                            field (cons field)
                            mod   (cons (node mod)))})))))

(def junction->tag
  {"AND" :and
   "&&"  :and
   "OR"  :or
   "||"  :or})

(def junction
  (-> (p/alt (p/word "AND") (p/word "&&") (p/word "OR") (p/word "||"))
      (p/value junction->tag)))

(def junction-clause
  (-> (p/group *ws junction +ws clause)
      (p/value (fn [[_ junction _ clause]] [junction clause]))))

(def query
  (->
   (p/group clause (p/*many junction-clause))
   (p/value (fn [[clause clauses]]
              (if-not (seq clauses)
                clause
                {:tag     :q
                 :content (reduce (fn [query [junction right]]
                                    (concat query (list (node junction) right)))
                                  (list clause)
                                  clauses)})))))

(def field-aliases
  "Aliasing of index field names, mostly translations."
  {"autor"         "author_s"
   "author"        "author_s"
   "red"           "editor_s"
   "editor"        "editor_s"
   "def"           "definitions_txt"
   "definitions"   "definitions_txt"
   "fehler"        "errors_ss"
   "errors"        "errors_ss"
   "form"          "forms_ss"
   "forms"         "forms_ss"
   "datum"         "timestamp_dt"
   "timestamp"     "timestamp_dt"
   "klasse"        "pos_s"
   "pos"           "pos_s"
   "quelle"        "source_s"
   "source"        "source_s"
   "status"        "status_s"
   "tranche"       "tranche_s"
   "typ"           "type_s"
   "type"          "type_s"
   "ersterfassung" "provenance_s"
   "provenance"    "provenance_s"})

(defn translate-field-names
  [{:keys [tag content] :as node}]
  (if (= :field tag)
    (let [k (gx/text node)]
      (if-let [k* (field-aliases k)]
        (assoc node :content (list k*))
        node))
    (if content
      (update node :content (partial map translate-field-names))
      node)))

(defn expand-date-terms
  "Date literals can be specified as month or day prefixes, i.e. `2020-10` or
  `2020-11-01`."
  [{:keys [content] :as node}]
  (if (string? node)
    (let [s* (-> node
                 (str/replace #"^(\d{4}-\d{2})$" "$1-01")
                 (str/replace #"^(\d{4}-\d{2}-\d{2})$" "$1T00\\\\:00\\\\:00Z"))]
      (if (not= node s*) s* node))
    (if content
      (update node :content (partial map expand-date-terms))
      node)))

(def parse
  (comp expand-date-terms translate-field-names (partial p/parse query)))

(defn valid?
  "A valid query can be parsed/transformed"
  [q]
  (try (parse q) true (catch Throwable _ false)))

(defn node->str
  [{:keys [tag content] {:keys [start end opts]} :attrs :as node}]
  (if-not tag
    (str node)
    (let [content (apply str (map node->str content))]
      (cond->
          (condp = tag
            :v        content
            :match    content
            :phr      (str "\"" content "\"")
            :re       (str "/" content "/")
            :not      "!"
            :must     "+"
            :must-not "-"
            :to       " TO "
            :and      " AND "
            :or       " OR "
            :range    (str (if (= start "exclusive") "{" "[")
                           content
                           (if (= end   "exclusive") "}" "]"))
            :sub      (str "(" content ")")
            :field    (str content ":")
            content)
        opts (str (str/join opts))))))

(defn ->str
  [v]
  (let [v (cond-> v (vector? v) (gx/sexp->node))
        v (cond-> v (map? v) (node->str))]
    v))

(def roundtrip
  (comp ->str parse))

(comment
  (roundtrip "tp:(\"RA\" OR \"B\\\"UG\")")
  (roundtrip "\"test \\\" 1\"~2")
  (roundtrip "test^2")
  (roundtrip "/ahd\\/jg\\kh/")
  (roundtrip "a:a* AND b? OR /test[ab]/ AND \"t \\\"a*\"")
  (roundtrip "[* TO b} AND test:\"a*\""))
