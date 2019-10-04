(ns zdl-lex-wikimedia.part-of-speech)

(def wkt
  #{#_"Abkürzung" #_"Abkürzung (Deutsch)" "Adjektiv" "Adverb" "Affix"
  "Antwortpartikel" "Artikel" #_"Buchstabe" #_"Deklinierte Form"
  #_"Dekliniertes Gerundivum" "Demonstrativpronomen" "Eigenname" #_"Englisch"
  #_"Enklitikon" #_"Erweiterter Infinitiv" "Fokuspartikel" #_"Formel"
  #_"Gebundenes Lexem" #_"Geflügeltes Wort" "Gradpartikel" #_"Grußformel" #_"Hilfsverb"
  "Indefinitpronomen" "Interjektion" #_"International" "Interrogativadverb"
  "Interrogativpronomen" #_"Klitikon" #_"Komparativ" #_"Konjugierte Form"
  "Konjunktion" "Konjunktionaladverb" "Kontraktion" "Lokaladverb" #_"Merkspruch"
  "Modaladverb" "Modalpartikel" "Nachname" "Negationspartikel" "Numerale"
  "Onomatopoetikum" #_"Ortsnamengrundwort" "Partikel" #_"Partizip I" #_"Partizip II"
  "Personalpronomen" "Possessivpronomen" #_"Postposition" "Pronomen"
  "Pronominaladverb" #_"Präfix" #_"Präfixoid" "Präposition" "Pseudopartizip"
  #_"Redewendung" "Reflexivpronomen" "Relativpronomen" "Reziprokpronomen"
  #_"Sprichwort" #_"Straßenname" #_"Subjunktion" "Substantiv" #_"Suffix" #_"Suffixoid"
  #_"Superlativ" "Temporaladverb" "Toponym" "Verb" "Vergleichspartikel" "Vorname"
  #_"Wiederholungszahlwort" #_"Wortverbindung" #_"Zahlklassifikator" #_"Zahlzeichen"
  #_"Zirkumposition" #_"gebundenes Lexem"})

(let [derived-forms #{"Alte Schreibweise"
                      "Deklinierte Form"
                      "Dekliniertes Gerundivum"
                      "Erweiterter Infinitiv"
                      "Komparativ"
                      "Konjugierte Form"
                      "Partizip I"
                      "Partizip II"
                      "Schweizer und Liechtensteiner Schreibweise"
                      "Superlativ"
                      "Umschrift"}]
  (defn basic? [s]
    (not (derived-forms s))))

(def wkt->zdl
  {"Abkürzung" nil
   "Abkürzung (Deutsch)" nil
   "Adjektiv" "Adjektiv"
   "Adverb" "Adverb"
   "Affix" "Affix"
   "Antwortpartikel" nil
   "Artikel" nil
   "Buchstabe" nil
   "Deklinierte Form" nil
   "Dekliniertes Gerundivum" nil
   "Demonstrativpronomen" "Demonstrativpronomen"
   "Eigenname" "Eigenname"
   "Englisch" nil
   "Enklitikon" nil
   "Erweiterter Infinitiv" nil
   "Fokuspartikel" "Adverb"
   "Formel" nil
   "Gebundenes Lexem" nil
   "Geflügeltes Wort" nil
   "Gradpartikel" "Adverb"
   "Grußformel" "Ausruf"
   "Hilfsverb" nil
   "Indefinitpronomen" "Indefinitpronomen"
   "Interjektion" nil
   "International" nil
   "Interrogativadverb" "Pronominaladverb"
   "Interrogativpronomen" "Interrogativpronomen"
   "Klitikon" nil
   "Komparativ" "Komparativ"
   "Konjugierte Form" nil
   "Konjunktion"  "Konjunktion" 
   "Konjunktionaladverb" "Konjunktion"
   "Kontraktion" nil
   "Lokaladverb" "Adverb"
   "Merkspruch" nil
   "Modaladverb" "Adverb"
   "Modalpartikel" "Adverb"
   "Nachname" "Eigenname"
   "Negationspartikel" "Adverb"
   "Numerale" "Kardinalzahl"
   "Onomatopoetikum" nil
   "Ortsnamengrundwort" nil
   "Partikel" "Adverb"
   "Partizip I" nil
   "Partizip II" nil
   "Personalpronomen" "Personalpronomen"
   "Possessivpronomen" "Possessivpronomen"
   "Postposition" nil
   "Pronomen" "Pronomen"
   "Pronominaladverb" "Pronominaladverb"
   "Präfix" "Affix"
   "Präfixoid" "Affix"
   "Präposition" "Präposition"
   "Pseudopartizip" "partizipiales Adjektiv"
   "Redewendung" nil
   "Reflexivpronomen" "Reflexivpronomen"
   "Relativpronomen" "Relativpronomen"
   "Reziprokpronomen" "reziprokes Pronomen"
   "Sprichwort" nil
   "Straßenname" "Eigenname"
   "Subjunktion" "Konjunktion"
   "Substantiv" "Substantiv"
   "Suffix" "Affix"
   "Suffixoid" "Affix"
   "Superlativ" "Superlativ"
   "Temporaladverb" "Adverb"
   "Toponym" "Eigenname"
   "Verb" "Verb"
   "Vergleichspartikel" "Adverb"
   "Vorname" "Eigenname"
   "Wiederholungszahlwort" "Adverb"
   "Wortverbindung" nil
   "Zahlklassifikator" nil
   "Zahlzeichen" nil
   "Zirkumposition" nil
   "gebundenes Lexem" nil})
