(ns zdl.lex.article.qa
  (:require
   [clojure.string :as str]
   [zdl.lex.article :as article]
   [gremid.xml :as gx])
  (:import (java.text Normalizer Normalizer$Form)))

;; # Validate

(def latin-chars
  ;; note: there's no decomposition for ø
  "abcdefghijklmnopqrstuvwxyzßABCDEFGHIJKLMNOPQRSTUVWXYZÆæĐđŁłŒœØø€£ðʒ")

(def greek-chars
  "λθξνόησιϛεϊδωο")

(def arabic-figures
  "0123456789")

(def diacritics
  "\u0300\u0301\u0302\u0303\u0304\u0306\u0308\u030a\u030b\u030c\u0327\u0328")

(def whitespace
  "space, newline, zero width space, zero width joiner"
  " \n\u200b\u200d")

(def punctuation
  "bare minimum for //Definition et al. (note: RIGHT SINGLE QUOTATION MARK!)"
  ".,()’-»«")

(def punctuation-extended
  "slightly extended for text like data (note: SOLIDUS vs. FRACTION SLASH!)"
  ":;?!/⁄–›‹%‰&=§°+†*@¬×·$€⊆±")

(def punctuation-extra
  "even more extended for //Fundstelle"
  "[]_~")

(def grammar-symbols
  "-_/")

(defn invalid-chars-fn
  [& sets]
  (let [cs (into #{} (apply str sets))]
    (fn [s]
      (when-let [data (some->>
                       (Normalizer/normalize s Normalizer$Form/NFKD)
                       (remove cs) (distinct) (seq) (vec))]
        {:type :invalid-chars :data data}))))

(def check-all-chars
  (invalid-chars-fn
   whitespace latin-chars greek-chars arabic-figures diacritics
   punctuation punctuation-extended punctuation-extra))

(def check-phrase-chars
  (invalid-chars-fn
   whitespace arabic-figures latin-chars diacritics punctuation))

(def check-text-chars
  (invalid-chars-fn
   whitespace latin-chars greek-chars arabic-figures diacritics
   punctuation punctuation-extended))

(def check-grammar-chars
  (invalid-chars-fn
   whitespace latin-chars arabic-figures diacritics punctuation
   grammar-symbols))

(def parenthesis-exceptions
  #"(?:\s[a-c]\))|(?:\u200b:-?[()])")

(def non-parenthesis
  #"[^»«\(\)\[\]]")

(defn remove-matching-parentheses
  [s]
  (loop [before s]
    (let [after (str/replace before #"(?:»«)|(?:\(\))|(?:\[\])" "")]
      (if (= (count before) (count after))
        (not-empty before)
        (recur after)))))

(defn check-parentheses
  [s]
  (when-let [data (some->
                   s
                   (str/replace parenthesis-exceptions "")
                   (str/replace non-parenthesis "")
                   (remove-matching-parentheses))]
    {:type :unbalanced-parens :data data}))

(def abbreviation-whitelist
  #{"etw.", "jmd.", "jmds.", "jmdn.", "jmdm.", "bzw.", "usw.", "o.\u202fä.", "o.\u202fÄ.", "z.\u202fB."})

(defn tokenize
  [s]
  (str/split s #"\s+"))


(defn check-ends-with-punctuation
  [s]
  (when-not (re-seq #"(?:[….?!])|(?:[.?!]«)$" s)
    {:type :final-punctuation :data [(subs s (max 0 (- (count s) 2)))]}))

(defn check-unknown-abbreviations
  [s]
  (when-let [data (some->>
                   (tokenize s)
                   (filter #(str/ends-with? % "."))
                   (remove abbreviation-whitelist)
                   (distinct) (seq) (vec))]
    {:type :unknown-abbreviations :data data}))

(defn check-missing-whitespace
  [s]
  (when-let [data (some->>
                   (tokenize s)
                   (filter
                    (some-fn
                     ;; e.g. aB ,A )A -A
                     (partial re-seq #"[\p{Ll}\p{Pe}\p{Po}&&[^/\"']]\p{Lu}")
                     ;; e.g. a( A( .( -(
                     (partial re-seq #"[\p{Lu}\p{Ll}\p{Po}\p{Pd}]\p{Ps}")
                     (partial re-seq #"«[^\p{Pe}\p{Po}]")
                     (partial re-seq #"[^\p{Ps}]»")))
                   (distinct) (seq) (vec))]
    {:type :missing-whitespace :data data}))

(defn check-redundant-whitespace
  [s]
  (when-let [data (some->>
                   (concat
                    (re-seq #"[»(/]\s" s)
                    (re-seq #"\s[\p{Po}&&[^%&*†/…\"']]" s))
                   (distinct) (seq) (vec))]
    {:type :redundant-whitespace :data data}))

(def checks-by-element
  {:Belegtext         [check-text-chars
                       check-parentheses
                       check-ends-with-punctuation
                       check-missing-whitespace
                       check-redundant-whitespace]
   :Definition        [check-phrase-chars
                       check-parentheses
                       check-unknown-abbreviations
                       check-missing-whitespace
                       check-redundant-whitespace]
   :Fundstelle        [check-all-chars]
   :Kollokation       [check-all-chars]
   :Formangabe        [check-grammar-chars]
   :Paraphrase        [check-phrase-chars]
   :Kompetenzbeispiel [check-parentheses]})

(defn check-node
  [node check-fns]
  (when (seq check-fns)
    (when-let [s (first (article/texts node))]
      (map #(assoc % :ctx node) (remove nil? (map #(% s) check-fns))))))

(defn check-typography
  [node]
  (when (map? node)
    (let [{:keys [tag content]} node]
      (concat
       (when-let [checks (checks-by-element tag)]
         (check-node node checks))
       (mapcat check-typography content)))))

;; # Fix Typography

(defn gloss?
  [node]
  (= :Belegtext (:tag node)))

(defn fix-typography'
  [s]
  (-> s
      (str/replace "..." "…")
      (str/replace ". . ." "…")
      (str/replace "—", "–") ; EM DASH → EN DASH
      (str/replace " -- ", " – ")
      (str/replace #"\s([\.,;])\s" "$1 ")))

(defn fix-typography
  ([node]
   (fix-typography (gloss? node) node))
  ([in-gloss? node]
   (if (string? node)
     (cond-> node in-gloss? (fix-typography'))
     (update node :content
             (partial map (partial fix-typography (or in-gloss? (gloss? node))))))))

;; # Enumerate senses

(defn sense?
  [node]
  (= :Lesart (:tag node)))

(defn assoc-sense-num'
  [[content next-sense-num] node]
  (if (sense? node)
    (let [node (assoc node ::n next-sense-num)]
      [(conj content node) (inc next-sense-num)])
    [(conj content node) next-sense-num]))

(defn assoc-sense-nums'
  [{:keys [content] :as node}]
  (if-not content
    node
    (let [content (map assoc-sense-nums' content)]
      (if-not (second (filter sense? content))
        (assoc node :content content)
        (let [content (first (reduce assoc-sense-num' [[] 0] content))]
          (assoc node :content content))))))

(def nums
  [(into [] (map #(format "%d." %) (range 1 30)))
   (into [] (map #(format "%s)" %) "abcdefghijklmnopqrstuvwxyz"))
   (into [] (map #(format "%s)" %) "αβγδεζηθικλμνξοπρστυφχψω"))])

(defn enumerate-senses
  ([node]
   (enumerate-senses 0 (assoc-sense-nums' node)))
  ([level {::keys [n] :keys [content] :as node}]
   (cond-> node
     (and (<= level 1) n) (assoc-in [:attrs :n] (get-in nums [level n]))
     (and (>  level 1) n) (assoc-in [:attrs :n] nil)
     content              (update
                           :content
                           (partial
                            map
                            (partial
                             enumerate-senses
                             (cond-> level (sense? node) (inc))))))))

;; # Red-1 to Red-2

(defn red-1->red-2
  [node]
  (if (= :Artikel (:tag node))
    (assoc-in node [:attrs :Status] "Red-2")
    (if (string? node)
      node
      (update node :content (partial map red-1->red-2)))))

(defn edit
  [file]
  (try
    (let [xml     (article/read-xml file)
          article (gx/element :Artikel xml)]
      (when (= "Red-1" (gx/attr :Status article))
        (let [wdg?   (str/includes? (or (gx/attr :Quelle article) "") "WDG")
              edited (cond-> (fix-typography xml) (not wdg?) (enumerate-senses))
              edited (red-1->red-2 edited)]
          (when-not (= xml edited) edited))))
    (catch Throwable _)))
