import logging
import lxml.etree as etree
import Mappings, Readers

class GenericDictionary(object):
    '''A basic/generic dictionary.

    This class models all interfaces common to the dictionaries used at DWDS.'''


    def __init__(self, files, input_format, embedded_forms=False):
        '''
        '''
        
        self.logger = logging.getLogger(GenericDictionary.__name__)
        
        self.target_tag, self.reader = {
                'dwdswb-xml': (Mappings.TAG['dwds:Artikel'], Readers.DWDS()),
                'tei-xml': (Mappings.TAG['tei:entry'], Readers.TEI(embedded_forms=embedded_forms)),
                'mediawiki-xml': (Mappings.TAG['mw:page'], Readers.Wiktionary()),
        }[input_format]

        self.embedded_forms = embedded_forms
        self._raw_files = files


    def __iter__(self):
        '''Returns each article of the dictionary.
        '''

        parser_options = {
                'load_dtd': True,
                'resolve_entities': True,
                'remove_comments': True,
                'remove_pis': True,
        }
        
        for raw_file in self._raw_files:
            
            self.logger.debug('Parsing %s', raw_file)

            # we use iterparse to keep the memory footprint small for huge input files
            for _, element in etree.iterparse(raw_file, **parser_options):
                if element.tag == self.target_tag:
                    try:
                        for i in self.reader.pythonize(element, keep_raw_data=True):
                            yield i
                    except (Readers.NoLexicalInformation, Readers.NoGermanBaseform) as error:
                        self.logger.info(error.message)
                    element.clear() # to keep memory footprint small on huge files

                if element.getparent() is None:
                    break # workaround for https://bugs.launchpad.net/bugs/1185701
