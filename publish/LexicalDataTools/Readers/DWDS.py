import logging
import lxml.etree as etree
import Microstructure, Mappings

class DWDS(object):

    def __init__(self):
        self.logger = logging.getLogger(DWDS.__name__)


    def normalize_whitespace(self, text, keep_head_tail=False):
        '''
        '''
        if keep_head_tail:
            raise NotImplementedError
        return ' '.join(text.split())


    def pythonize(self, xml_snippet, keep_raw_data=False):
        '''Converts a serialized DWDS format article (XML) into a Python object.
        '''

        article = Microstructure.Article()
        article.id = xml_snippet.get(Mappings.TAG['id'])
        article.type = xml_snippet.get('Typ')
        article._raw_data = xml_snippet if keep_raw_data else None

        for form in etree.ETXPath('./%(dwds:Formangabe)s' % Mappings.TAG)(xml_snippet):
            
            lemma = Microstructure.Lemma(Mappings.ARTICLE_TYPE[article.type])
            
            lemma.orthographic_forms = [
                    (self.normalize_whitespace(o.text), o.get('hidx'), o.get('Typ') or 'AR_G' )
                    for o in form.findall(Mappings.TAG['dwds:Schreibung'] )
            ]
            
            lemma.parts_of_speech = [
                    Mappings.PART_OF_SPEECH[pos.text]
                    for pos in etree.ETXPath('.//%(dwds:Wortklasse)s' % Mappings.TAG)(form)
            ]

            lemma.genders = [
                    Mappings.GENUS[gender.text]
                    for gender in etree.ETXPath('.//%(dwds:Genus)s' % Mappings.TAG)(form)
            ]

            article.lemmas.append(lemma)

        # this is just a hack to get the number of leaf nodes in the sense structure
        #for sense in etree.ETXPath('.//%(dwds:Lesart)s[@n and not(%(dwds:Lesart)s[@n])]' % Mappings.TAG)(xml_snippet):
        #    article.senses.append(Microstructure.Sense())
        for sense in etree.ETXPath('.//%(dwds:Lesart)s' % Mappings.TAG)(xml_snippet):
            text = ''.join(etree.ETXPath('.//text()')(sense)).strip()
            n = sense.get('n')
            if n is not None and n[0].isdigit() and text != '':
                article.senses.append(Microstructure.Sense())

        yield article
