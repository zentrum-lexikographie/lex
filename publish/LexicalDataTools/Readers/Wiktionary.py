# encoding: utf8

import logging, re
import mwparserfromhell as mwp
import Microstructure, Mappings

class NoLexicalInformation(Exception): pass

class NoGermanBaseform(Exception): pass

class Wiktionary(object):

    def __init__(self):
        self.logger = logging.getLogger(Wiktionary.__name__)


    def _mw_normalize_sense_count(self, s):

        s = s.strip('[] \n')
        n = 0
        for p in [ p.strip() for p in s.split(',') ]:
            if p.isdigit():
                n += 1
            else:
                start, stop = p.split(u'–')
                n += int(stop) - int(start)
        return n


    def _mw_analyze_external_refs(self, article, section):
        '''
        '''
        n = [0, 0]
        for index, target in enumerate( ('Ref-DWDS', 'Ref-Duden') ):
            match = section.filter_templates(matches=target)
            if match:
                try:
                    n[index] = self._mw_normalize_sense_count(section.get(section.index(match[0]) - 1).value)
                except (ValueError, AttributeError):
                    pass


        self.logger.info('ExternalRefs for %s: %i, %s, %s',
                article.lemmas[0], len(article.senses), n[0], n[1])


    def _mw_get_senses(self, section):
        '''
        '''
        # not sure whether this is the best approach
        SENSE_EXTRACT_PATTERN = re.compile('{{Bedeutungen.*?}}(.*?){{', re.U|re.DOTALL)
        SENSE_COUNT_PATTERN = re.compile('(:\[\\d+])', re.U|re.DOTALL)
        senses = []

        match = SENSE_EXTRACT_PATTERN.search(str(section))
        if match is not None:
            for i in re.findall(SENSE_COUNT_PATTERN, match.group(1)):
                senses.append(Microstructure.Sense())

        return senses


    def pythonize(self, xml_snippet, keep_raw_data=False):
        '''Converts a serialized MediaWiki format Wiktionary article (XML) into a Python object.
        '''

        title = xml_snippet.find('./%(mw:title)s' % Mappings.TAG).text.strip()

        # see whether this is actually a lexical article
        if xml_snippet.find('./%(mw:ns)s' % Mappings.TAG).text.strip() != '0':
            raise NoLexicalInformation('Special Wiki page "%s"' % title)
        
        raw_wikitext = xml_snippet.find(Mappings.TAG['mw:revision']).find(Mappings.TAG['mw:text']).text or ''
        wikitext = mwp.parse(raw_wikitext)

        # at level 3 (=== Section ===) language/POS tuples are identified
        for section in wikitext.get_sections(levels=[3]):

            article = Microstructure.Article()
            article.id = xml_snippet.find('./%(mw:id)s' % Mappings.TAG).text.strip()
            article.type = 'main'
            article._raw_data = wikitext if keep_raw_data else None

            lemma = Microstructure.Lemma('main')
            article.lemmas.append(lemma)
            lemma.orthographic_forms.append( (title, None, None) )

            languages = set([])
            
            # filter form based information
            for template in section.filter_templates(matches='^{{Wortart'):

                try:
                    lemma.parts_of_speech.add(Mappings.PART_OF_SPEECH[unicode(template.get(1).value.strip())])
                except KeyError:
                    self.logger.warning('No POS mapping for "%s"', unicode(template.get(1).value))

                try:
                    languages.add(unicode(template.get(2).value))
                except ValueError:
                    pass

            # record POS tags for lemmas explicitly marked as German
            # TODO: Occasianally there are dialects
            # like 'Plattdeutsch' in the data.
            # Should we include those?
            if 'INFLECTED' in lemma.parts_of_speech:
                self.logger.debug('Ignoring inflected form "%s"', title)
            elif len(lemma.parts_of_speech) > 1:
                lemma.parts_of_speech.discard(None)
            else:
                if 'Deutsch' in languages or 'International' in languages:
                    article.senses = self._mw_get_senses(section)
                    self._mw_analyze_external_refs(article, section)
                    yield article
                else:
                    self.logger.info('No German baseform "%s" (%s)' %
                            (title, ', '.join((languages))))
