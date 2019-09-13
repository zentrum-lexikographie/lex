# encoding: utf8
import lxml.etree as etree

# translates resource specific vocabulary to a common representation
# which is inspired by STTS but strongly simplified
PART_OF_SPEECH = {
        # nouns
        u'Substantiv': 'N',
        u'Substantivierter Infinitiv': 'N',
        u'Eigenname': 'NE',
        u'Vorname': 'NE',
        u'Nachname': 'NE',
        u'Familienname': 'NE',
        u'Gentilname': 'NE',
        u'Kognomen': 'NE',
        u'Toponym': 'NE',
        # verbs
        u'Verb': 'V',
        u'Hilfsverb': 'V',
        u'Imperativ': 'V',
        u'Erweiterter Infinitiv': 'V',
        # ad*
        u'Adjektiv': 'ADJ',
        u'partizipiales Adjektiv': 'ADJ',
        u'Partizip I': 'ADJ',
        u'Partizip Präteritum': 'ADJ',
        u'Partizip II': 'ADJ',
        u'Superlativ': 'ADJ',
        u'Komparativ': 'ADJ',
        u'Gerundium': 'ADJ',
        u'Adverb': 'ADV',
        u'partizipiales Adverb': 'ADV',
        u'Temporaladverb': 'ADV',
        u'Kausaladverb': 'ADV',
        u'Pronominaladverb': 'ADV', # STTS: PROP
        u'Konjunktionaladverb': 'ADV',
        u'Modaladverb': 'ADV',
        u'Lokaladverb': 'ADV',
        u'Interrogativadverb': 'ADV',
        # pronouns
        u'Pronomen': 'P',
        u'Indefinitpronomen': 'P',
        u'Possessivpronomen': 'P',
        u'Personalpronomen': 'P',
        u'Demonstrativpronomen': 'P',
        u'Relativpronomen': 'P',
        u'Interrogativpronomen': 'P',
        u'Reziprokpronomen': 'P',
        u'reziprokes Pronomen': 'P',
        u'Reflexivpronomen' : 'P',
        u'Reflexives Personalpronomen': 'P',
        u'Reflexives Possessivpronomen': 'P',
        # numerals
        u'Ordinalzahl': 'FIG',
        u'Kardinalzahl': 'FIG',
        u'Bruchzahl': 'FIG',
        u'Numerale': 'FIG',
        u'Zahlzeichen': 'FIG',
        # *positions
        u'Präposition': 'AP',
        u'Postposition': 'AP',
        # affixes
        u'Formativ': 'AFF',
        u'Affix': 'AFF',
        u'Präfix': 'AFF',
        u'Präfixoid': 'AFF',
        u'Suffix': 'AFF',
        u'Suffixoid': 'AFF',
        u'Ortsnamengrundwort': 'AFF',
        u'Gebundenes Lexem': 'AFF',
        u'neoklassisches Formativ': 'AFF',
        # particles
        u'Partikel': 'PTK',
        u'abtrennbare Verbpartikel': 'PTK',
        u'Negationspartikel': 'PTK',
        u'Antwortpartikel': 'PTK',
        u'Gradpartikel': 'PTK',
        u'Fokuspartikel': 'PTK',
        u'Modalpartikel': 'PTK',
        u'Vergleichspartikel': 'PTK',
        # multi word expressions
        u'Mehrwortbenennung': 'MWA',
        u'Wortverbindung': 'MWA',
        u'Redewendung': 'MWA',
        u'Sprichwort': 'MWA',
        u'Merkspruch': 'MWA',
        u'Geflügeltes Wort': 'MWA',
        # everything else
        u'Artikel': 'ART',
        u'bestimmter Artikel': 'ART',
        u'Konjunktion': 'KO',
        u'Subjunktion': 'KO',
        u'Partikel': '',
        u'Ausruf': 'I',
        u'Interjektion': 'I',
        # things below are no proper POS classes anyway
        # but appear in Wiktionary
        u'Abkürzung': None,
        u'Onomatopoetikum': None,
        u'Grußformel': None,
        u'Zusammenbildung': None,
        u'Kontraktion': None,
        u'Zahlklassifikator': None, # e.g. 'Dutzend' in Wikipedia
        u'Umschrift': None,
        u'Symbol': None,
        u'Schriftzeichen': None,
        u'Hiragana': None,
        u'Katakana': None,
        u'Buchstabe': None,
        u'Enklitikon': None,
        None: None,
        u'Deklinierte Form': 'INFLECTED',
        u'Konjugierte Form': 'INFLECTED',
        u'Flektierte Form': 'INFLECTED',
        u'Exzessiv': 'INFLECTED',
}

GENUS = {
        'mask.': 'masculine',
        'fem.': 'feminine',
        'neutr.': 'neuter',
}

ARTICLE_TYPE = {
        'Basisartikel': 'basic',
        'Minimalartikel': 'mini',
        'Vollartikel': 'main',
}
    
# Tags ar set up with artificial prefixes (dwds:*) so we can keep them in a single dict
TAG = { 'id': etree.QName('http://www.w3.org/XML/1998/namespace', 'id') }
for tag in ('TEI', 'entry', 'form', 'orth', 'gen', 'pos', 'sense', ):
    TAG['tei:'+tag] = etree.QName('http://www.tei-c.org/ns/1.0', tag)
for tag in ('DWDS', 'Artikel', 'Formangabe', 'Schreibung', 'Wortklasse', 'Genus', 'Lesart', ):
    TAG['dwds:'+tag] = etree.QName('http://www.dwds.de/ns/1.0', tag)
for tag in ('page', 'text', 'id', 'ns', 'title', 'revision', ):
    TAG['mw:'+tag] = etree.QName('http://www.mediawiki.org/xml/export-0.8/', tag)
