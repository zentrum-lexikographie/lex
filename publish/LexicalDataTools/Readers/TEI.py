import logging
import lxml.etree as etree
import Microstructure, Mappings

class TEI(object):
    '''
    '''

    def __init__(self, embedded_forms):
        self.logger = logging.getLogger(TEI.__name__)
        self.embedded_forms = embedded_forms


    def pythonize(self, xml_snippet, keep_raw_data=False):
        '''Converts a serialized TEI format article (XML) into a Python object.
        '''
        
        article = Microstructure.Article()
        article.id = xml_snippet.get(Mappings.TAG['id'])
        article.type = xml_snippet.get('type')
        article._raw_data = xml_snippet if keep_raw_data else None

        path = './/%(tei:form)s' if self.embedded_forms else './%(tei:form)s'

        for form in etree.ETXPath(path % Mappings.TAG)(xml_snippet):

            parent = form.getparent()
            if parent.tag == Mappings.TAG['tei:sense']:
                # do not record sense internal form constraints
                break
            lemma = Microstructure.Lemma(parent.get('type') if parent.tag == Mappings.TAG['tei:entry'] else 'sub')
            
            for o in form.findall(Mappings.TAG['tei:orth']):
                expand = o.get('expand')
                if expand is not None:
                    lemma.orthographic_forms.extend(
                            [ (x.replace('_', ' '), o.get('hidx'), None)
                                    for x in expand.split()
                            ]
                    )
                elif o.text:
                    lemma.orthographic_forms.append( (o.text, o.get('hidx'), None) )
                else:
                    self.logger.debug('Empty orthographic form.')
            
            lemma.parts_of_speech = [
                    pos.get('value')
                    for pos in etree.ETXPath('.//%(tei:pos)s' % Mappings.TAG)(form)
            ]

            lemma.genders = [
                    gender.get('value')
                    for gender in etree.ETXPath('.//%(tei:gen)s' % Mappings.TAG)(form)
            ]

            article.lemmas.append(lemma)
        
        # this is just a hack to get the number of leaf nodes in the sense structure
        #for sense in etree.ETXPath('.//%(tei:sense)s[@n and not(%(tei:sense)s[@n])]' % Mappings.TAG)(xml_snippet):
        #    article.senses.append(Microstructure.Sense())

        
        yield article
