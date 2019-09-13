# encoding: utf8

import logging
import lxml.etree as etree

class HeadwordListFormatter(object):
    '''Creates headword lists in different output formats.
    '''

    def __init__(self, dictionary):
        self.dictionary = dictionary
        self.logger = logging.getLogger(HeadwordListFormatter.__name__)


    def plaintext(self):
        '''Returns a TAB separated list of headwords.
        '''
        
        data = []
        for article in self.dictionary:
            line_cache = set([])
            for lemma in article.lemmas:
                for oform, hidx, valid in lemma.orthographic_forms:
                    line = '\t'.join( (
                        article.id,
                        (oform + '%' + (hidx or '' )).rstrip('%').replace(' ', '_'),
                        lemma.type,
                        '|'.join( pos or '' for pos in lemma.parts_of_speech ) or 'NONE',
                        '|'.join( gen or '' for gen in lemma.genders ) or 'NONE',
                        valid or 'NONE',
                        #str(len(article.senses) or 1), # at least on sense
                        #str(len(' '.join(''.join(etree.XPath('.//text()')(article._raw_data)).split()))),
                        #str(True if article.type == 'Minimalartikel' else False)
                        #' '.join(''.join(etree.XPath('.//text()')(article._raw_data)).split()),
                    ) )
                    # TODO: line_cache is only needed for DWDS dictionaries
                    if not line in line_cache:
                        data.append(line)
                        line_cache.add(line)
        return '\n'.join(data)


    def lmf(self, options):
        '''Returns an LMF formatted list of headwords.
        '''

        template = '''<?xml version="1.0"?>
            <LexicalResource dtdVersion="16">
                <GlobalInformation>
                    <feat att="label" val="DWDS Lexical Resources (http://www.dwds.de/)" />
                    <feat att="comment" val="automatically created LMF compliant version" />
                    <feat att="languageCoding" val="ISO 639-3" />
                </GlobalInformation>
            <Lexicon>
                <feat att="label" val="" />
                <feat att="creationDate" val="" />
                <feat att="version" val="" />
                <feat att="language" val="deu" />
                <feat att="comment" val="includes headword forms, part-of-speech and grammatical gender (for noun entries)" />
            </Lexicon>
        </LexicalResource>'''
        
        lmfdoc = etree.fromstring(template,
                etree.XMLParser(remove_blank_text=True))
        lexicon = lmfdoc.find('.//Lexicon')
        etree.ETXPath('.//feat[@att="label"]')(lmfdoc)[1].set('val', options.mdname)
        etree.ETXPath('.//feat[@att="creationDate"]')(lmfdoc)[0].set('val', options.mddate)
        etree.ETXPath('.//feat[@att="version"]')(lmfdoc)[0].set('val', options.mdversion)

        for article in self.dictionary:
            self.logger.debug(article.lemmas[0])
            self.logger.debug(article.lemmas[0].parts_of_speech[0])
            lexical_entry = etree.SubElement(lexicon, 'LexicalEntry', id=article.id)
            etree.SubElement(lexical_entry, 'feat',
                    att='partOfSpeech',
                    val=article.lemmas[0].parts_of_speech[0] or '') # only the first POS is used!
            etree.SubElement(lexical_entry, 'feat',
                    att='type',
                    val=article.type)

            lemma = etree.SubElement(lexical_entry, 'Lemma')
            lemma_representation, hidx, _ = article.lemmas[0].orthographic_forms[0]
            lemma_representation = (lemma_representation + '%' + (hidx or '')).rstrip('%').replace(' ', '_')
            etree.SubElement(lemma, 'feat', att='writtenForm', val=lemma_representation)

            # encode all forms as WordForms
            for l in article.lemmas:
                wordform_cache = set([])
                for wordform, hidx, valid in l.orthographic_forms:
                    if not wordform in wordform_cache:
                        wordform_cache.add( wordform )
                        variant = etree.SubElement(lexical_entry, 'WordForm')
                        etree.SubElement(variant, 'feat',
                                att='writtenForm',
                                val=wordform.replace(' ', '_')
                        )
                        for p in l.parts_of_speech:
                            etree.SubElement(variant, 'feat',
                                    att='partOfSpeech',
                                    val=p or ''
                            )
                        for g in l.genders:
                            etree.SubElement(variant, 'feat',
                                    att='grammaticalGender',
                                    val=g or ''
                            )

        return etree.tostring(lmfdoc, pretty_print=True, encoding='utf8',
                xml_declaration=True)
