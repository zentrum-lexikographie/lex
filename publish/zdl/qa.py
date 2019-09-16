#!/usr/bin/env python3

'''
This script does the automatic steps for the redaction process step 2
of the DWDS newly edited entries. This comprises all formal checks and
verifications of the data before the internal preview publication.

See http://odo.dwds.de/twiki/bin/view/Lexikographen/RedaktionsProzess
for detailed information on the complete redaction process.
'''

import unicodedata, re
import lxml.etree as et

from .oxygen import add_comment
from .exceptions import TOKENIZATION_EXCEPTIONS


class CheckerInfrastructure(object):
    '''
    '''

    TAG = {t: et.QName('http://www.dwds.de/ns/1.0', t)
           for t in ('DWDS', 'Artikel',
                     'Formangabe', 'Schreibung', 'Aussprache',
                     'Grammatik', 'Wortklasse',
                     'Genitiv', 'Plural', 'Numeruspraeferenz',
                     'Praesens', 'Praeteritum', 'Partizip_II',
                     'Komparationspraeferenz', 'Komparativ', 'Superlativ',
                     'Verweise', 'Verweis', 'Ziellemma', 'Ziellesart',
                     'Lesart', 'Definition', 'Paraphrase',
                     'Kollokationen', 'Verwendungsbeispiele',
                     'Beleg', 'Kollokation1', 'Kollokation2',
                     'Kompetenzbeispiel',
                     'Belegtext', 'Streichung', 'Loeschung', 'Fundstelle',
                     'Diasystematik', )}
    TAG['id'] = et.QName('http://www.w3.org/XML/1998/namespace', 'id')
    TAG['xml:space'] = et.QName('http://www.w3.org/XML/1998/namespace',
                                'space')

    CANONICAL_PI_LOCATIONS = (
        TAG['DWDS'],
        TAG['Artikel'],
        TAG['Formangabe'],
        TAG['Verweise'],
        TAG['Diasystematik'],
        TAG['Lesart'],
        TAG['Definition'],
        TAG['Beleg'],
        TAG['Kompetenzbeispiel'],
        TAG['Kollokation1'],
        TAG['Kollokation2'], )

    def __init__(self):
        self.reset()

    def mark(self, element, comment):
        '''Set or update @check on an arbitrary element.
        '''
        check = element.get('check').split()\
            if 'check' in element.attrib else []
        check.append(comment.replace(' ', '_'))
        element.set('check', ' '.join(check))

    def comment(self, element, comment):
        '''Attach a comment visible in oXygen to an arbitrary element.

        Comments also trigger objections (rejection).
        '''
        self.no_objections = False

        current = element
        while current.tag not in self.CANONICAL_PI_LOCATIONS:
            current = current.getparent()
        previous = current.getprevious()

        while previous is not None and not isinstance(previous, str):
            if comment in str(previous):
                return  # already commented
            previous = previous.getprevious()

        add_comment(current, comment, author='Redaktion2')

    def reset(self):
        self.no_objections = True


class StructureChecker(CheckerInfrastructure):
    '''
    '''
    ARABIC_NUMERAL_MARKERS = ['%i.' % x for x in range(1, 30)]
    LATIN_SMALL_LETTER_MARKERS = ['%s)' % x
                                  for x in 'abcdefghijklmnopqrstuvwxyz']

    def __init__(self):
        super(StructureChecker, self).__init__()

    def remove_xml_space_preserve(self, element):
        for e in element.iter('*'):
            if isinstance(element.tag, str) and\
               self.TAG['xml:space'] in element.attrib:
                element.attrib.pop(self.TAG['xml:space'])

    def reposition_pis(self, root):
        '''Move all PIs (i.e. oXygen comments) to canonical places.

        This is done to have //Belegtext as mixed content
        with at most on level of structuring. Even though in principle
        it is possible to mark spans of text with a comment in oXygen,
        at this phase of the lexicographical process we allow only
        the most general comments to stay in the sources.
        Those general comments are believed to focus on bigger units
        than spans of texts.'''

        for element in root.iter():
            if not isinstance(element.tag, str):

                current = element
                parent = current.getparent()

                # when moving PIs, don't forget about the tail
                previous = element.getprevious()
                if previous is None:
                    parent.text = (parent.text or '') + (element.tail or '')
                else:
                    previous.tail = (previous.tail or '') +\
                                    (element.tail or '')
                element.tail = ''

                # find target position
                while parent.tag not in self.CANONICAL_PI_LOCATIONS:
                    current = parent
                    parent = current.getparent()

                if element.text:
                    # comment_start
                    parent.insert(parent.index(current), element)
                else:
                    # comment_end
                    parent.insert(parent.index(current) + 1, element)

    def remove_stylesheet_pis(self, element):
        '''
        '''
        root = element

        while root.getparent() is not None:
            root = root.getparent()
        pi = root.getprevious()

        while pi is not None:
            if 'support/presentation/article.css' in pi.text:
                # we can only remove things from an element,
                # not from *before* an element,
                # so first move it, then remove it
                root.append(pi)
                root.remove(pi)
                break
            pi = pi.getprevious()

    def remove_redaction_pis(self, root):
        '''
        '''
        for element in root.iter():

            if not isinstance(element.tag, str):

                parent = element.getparent()

                if ('author="Redaktion2"' in element.text):

                    n = element.getnext()
                    level = 0

                    while n is not None:

                        if not isinstance(n.tag, str):

                            if (n.text or '') == '':

                                if level == 0:
                                    parent.remove(element)
                                    parent.remove(n)
                                    break
                                else:
                                    level -= 1
                            else:
                                level += 1
                        else:
                            pass

                        n = n.getnext()

    def remove_unneeded_deletions(self, element):
        '''
        '''

        for s in element.iter(str(self.TAG['Loeschung'])):

            parent = s.getparent()

            if (s.tail or '').strip() == '' and s.getnext() is None:
                parent.remove(s)
            elif s.getprevious() is None and (parent.text or '').strip() == '':
                parent.text = s.tail
                parent.remove(s)

    def hide_semantic_links(self, element):
        '''
        '''
        link_xpath = './/%(Lesart)s/%(Verweise)s/%(Verweis)s' % self.TAG
        for link in et.ETXPath(link_xpath)(element):
            if not link.get('type') in ('Antonym', 'Synonym', 'Assoziation'):
                link.set('class', 'invisible')

    def check_grammatical_info(self, element):
        '''
        '''
        grammar_xpath = './%(Formangabe)s/%(Grammatik)s' % self.TAG
        for grammar in et.ETXPath(grammar_xpath)(element):

            word_class = grammar.find(self.TAG['Wortklasse']).text

            if word_class == 'Substantiv':

                sg_form = grammar.find(self.TAG['Genitiv'])
                pl_form = grammar.find(self.TAG['Plural'])
                number_preference = grammar.find(self.TAG['Numeruspraeferenz'])

                # silent compatibility fixes
                if pl_form is not None and (pl_form.text or '').strip() == 'no_data':
                    pl_form.text = ''
                elif pl_form is not None and (pl_form.text or '').strip() == '-0':
                    pl_form.text = '-'
                if sg_form is not None and (sg_form.text or '').strip() == 'no_data':
                    sg_form.text = ''
                elif sg_form is not None and (sg_form.text or '').strip() == '-0':
                    sg_form.text = '-'

                # sanity checks
                # Numeruspraeferenz is set by lexicographers so this is the baseline
                if number_preference is not None and number_preference.text == 'nur im Singular':
                    if pl_form is not None:
                        self.comment(grammar, 'inkonsistente Flexionsangaben')
                elif number_preference is not None and number_preference.text == 'nur im Plural':
                    if sg_form is not None:
                        self.comment(grammar, 'inkonsistente Flexionsangaben')
                else:
                    # all forms have to be specified
                    if sg_form is None or pl_form is None:
                        self.comment(grammar, u'fehlende Flexionsangaben')

            elif word_class == 'Verb':

                past_tense = grammar.find(self.TAG['Praeteritum'])
                past_participle = grammar.find(self.TAG['Partizip_II'])

                if past_tense is None or past_participle is None:
                    self.comment(grammar, u'unvollständige Flexionsangaben')

            elif word_class in ('Adjektiv', 'Adverb', 'partizipiales Adjektiv'):
                preference = grammar.find(self.TAG['Komparationspraeferenz'])  # NOQA
                comparative = grammar.find(self.TAG['Komparativ'])  # NOQA
                superlative = grammar.find(self.TAG['Superlativ'])  # NOQA

                # TODO: sanity checks

            elif word_class == 'Mehrwortausdruck':
                pass

            elif word_class == 'Konjunktion':
                pass

            elif word_class == u'Präposition':
                pass

            else:
                raise NotImplementedError(
                    'wordclass %s not handled' % word_class
                )

    def expand_grammatical_atoms(self, article):
        '''Silently expand -(e)s and the like to -s and -es in //Genitiv.
        '''
        g_pattern = re.compile(r'\s*-\((?P<optional>e)\)(?P<mandatory>s)\s*')
        for g in article.findall(self.TAG['Genitiv']):
            g_match = g_pattern.match(g.text)
            if g_match is not None:
                parent = g.getparent()
                new = et.Element(self.TAG['Genitiv'])
                new.text = '-%(mandatory)s' % g_match.groupdict()
                parent.insert(parent.index(g), new)
                g.text = '-%(optional)s%(mandatory)s' % g_match.groupdict()

    def _generate_n_markers(self, senses, markers):
        if len(senses) > 1:
            for index, sense in enumerate(senses):
                sense.set('n', markers[index])

    def check_n_markers(self, element):
        '''User supplied @n takes precedence.
        '''
        sense = element.find('.//%(Lesart)s' % self.TAG)
        self.comment(sense, 'check @n on senses!')
        pass

    def insert_n_markers(self, element):
        '''Insert //Lesart@n attributes if there are no such attributes already.
        '''
        marked_senses = [l for l in element.findall('.//%(Lesart)s[@n]' % self.TAG)]
        if len(marked_senses) != 0:
            self.check_n_markers(element)
        else:
            level_1_senses = [l for l in element.findall('./%(Lesart)s' % self.TAG)]
            self._generate_n_markers(level_1_senses, self.ARABIC_NUMERAL_MARKERS)

            for sense in level_1_senses:
                level_2_senses = [l for l in sense.findall('./%(Lesart)s' % self.TAG)]
                self._generate_n_markers(level_2_senses, self.LATIN_SMALL_LETTER_MARKERS)

    def rigid_markup(self, element, author):
        '''There is a more rigid schema on the production server.
        '''

        if element.get('Autor') is None:
            element.set('Autor', author)

        for s in element.findall('.//%(Lesart)s/%(Formangabe)s/%(Schreibung)s' % self.TAG):
            if (s.text or '') == '':
                s.getparent().remove(s)


class TypographyChecker(CheckerInfrastructure):
    '''
    '''

    # note: there's no decomposition for ø
    _LATIN_CHARS = u'abcdefghijklmnopqrstuvwxyzßABCDEFGHIJKLMNOPQRSTUVWXYZÆæĐđŁłŒœØø€£ðʒ'
    _GREEK_CHARS = u'λθξνόησιϛεϊδωο'
    _ARABIC_FIGURES = u'0123456789'
    _DIACRITICS = u'\u0300\u0301\u0302\u0303\u0304\u0306\u0308\u030a\u030b\u030c\u0327\u0328'
    _WHITESPACE = u' \n\u200b\u200d'  # ZERO WIDTH SPACE, ZERO WIDTH JOINER

    # bare minimum for //Definition et al. (note: RIGHT SINGLE QUOTATION MARK!)
    _PUNCTUATION1 = u'.,()’-»«'

    # slightly extended for text like data (note: SOLIDUS vs. FRACTION SLASH!)
    _PUNCTUATION2 = u':;?!/⁄–›‹%‰&=§°+†*@¬×·$€⊆±'

    # even more extended for //Fundstelle
    _PUNCTUATION3 = u'[]_~'

    EXPECTED_CODEPOINTS = {
        'all': _WHITESPACE + _LATIN_CHARS + _GREEK_CHARS + _ARABIC_FIGURES + _DIACRITICS + _PUNCTUATION1 + _PUNCTUATION2 + _PUNCTUATION3,
        'phrase level': _WHITESPACE + _ARABIC_FIGURES + _LATIN_CHARS + _DIACRITICS + _PUNCTUATION1,
        'text level': _WHITESPACE + _LATIN_CHARS + _GREEK_CHARS + _ARABIC_FIGURES + _DIACRITICS + _PUNCTUATION1 + _PUNCTUATION2,
        'grammar': _WHITESPACE + _LATIN_CHARS + _ARABIC_FIGURES + _DIACRITICS + _PUNCTUATION1 + '.-_/ \n'
    }

    EXPECTED_ABBREVIATIONS = ('etw.', 'jmd.', 'jmds.', 'jmdn.', 'jmdm.')

    PARENTESIS_EXCEPTIONS = (
        # enumerations with parenthesis
        'a)', 'b)', 'c)',
        u'\u200b:-)', u'\u200b:)', u'\u200b:-(',
    )

    TRANSLITERATIONS = {
        u'C\'t ': u'C’t ',
        u'Brockhaus\' ': u'Brockhaus’ ',
        u'z.B.': u'z. B.',
        u'v.H.': u'v. H.',
        u' .': u'. ',
        u' %': u' % ',  # NO-BREAK SPACE
        u' ?': u'? ',
        u' !': u'! ',
        u' ;': u'; ',
        u' ,': u', ',
        u' :': u': ',
        u' «': u'« ',
        u'» ': u' »',
        u' ‹': u'‹ ',
        u'› ': u' ›',
        u'( ': u' (',
        u' )': u') ',
        u'...': u'…',
        u' - ': u' – ',  # hyphen to EN DASH
        u'—': u'–',   # EM DASH to EN DASH
        u'\r': '\n',
        u'\t': ' ',
        u' °': u'°',
        u'° ': u'°',
    }

    def __init__(self):
        super(TypographyChecker, self).__init__()

    def check_abbreviations_in_definitions(self, element):
        '''
        '''
        for definition in element.iter(self.TAG['Definition']):
            for token in (''.join(et.ETXPath('.//text()')(definition))).split():
                if token.endswith('.') and token not in self.EXPECTED_ABBREVIATIONS:
                    self.comment(definition, u'nicht erlaubte Abkürzung')

    def strip_and_correct_whitespace(self, element):
        '''Silently strip and correct whitespace anomalies.
        '''
        for e in element.iter('*'):

            if isinstance(element.tag, str) and len(e) == 0:

                text = e.text or ''
                tail = e.tail or ''
                left = e.getprevious()
                parent = e.getparent()

                if len(text) > 0 and text[0].isspace():
                    text = text.lstrip()
                    if left is not None:
                        left.tail = (left.tail or '') + ' '
                    else:
                        parent.text = (parent.text or '') + ' '

                if len(text) > 0 and text[-1].isspace():
                    text = text.rstrip()
                    tail = ' ' + tail.lstrip()

                e.text = text
                e.tail = tail

    def _check_chars(self, root, norm):

        string = ''.join(et.ETXPath('.//text()')(root))
        string = unicodedata.normalize('NFKD', string)

        for char in string:
            if char in self.EXPECTED_CODEPOINTS[norm]:
                pass
            else:
                self.comment(root, 'Zeichenfehler: %s' % repr(char))

    def check_chars(self, element):
        '''Check text with regard to expected characters per element type.
        '''

        for e in element.iter(str(self.TAG['Formangabe'])):
            self._check_chars(e, 'grammar')
        for e in element.iter(str(self.TAG['Definition'])):
            self._check_chars(e, 'phrase level')
        for e in element.iter(str(self.TAG['Paraphrase'])):
            self._check_chars(e, 'phrase level')
        for e in element.iter(str(self.TAG['Belegtext'])):
            self._check_chars(e, 'text level')
        for e in element.iter(str(self.TAG['Fundstelle'])):
            self._check_chars(e, 'all')

    def transliterate(self, element):
        '''
        Recursively scan tree and adjust certain character combinations to
        proper UNICODE.
        '''

        for e in et.ETXPath('.//*')(element):

            if isinstance(element.tag, str):
                text = e.text or ''
                tail = e.tail or ''

                for char, subst in self.TRANSLITERATIONS.iteritems():
                    text = text.replace(char, subst)
                    tail = tail.replace(char, subst)

                e.text = text
                e.tail = tail

        # for mixed content, more than one pass may be required
        for e in element.iter(str(self.TAG['Beleg'])):

            text = ''.join(et.ETXPath('.//text()')(e))

            for t in self.TRANSLITERATIONS:
                if t in text:
                    self.comment(e, 'Zeichenkombinationsfehler:%s' % t)
            if '..' in text:
                self.comment(e, 'Zeichenkombinationsfehler:..')

    def check_final_punctuation(self, element):

        for e in et.ETXPath('.//%(Beleg)s/%(Belegtext)s' % self.TAG)(element):
            t = ' '.join((''.join(et.ETXPath('.//text()')(e))).split())

            if len(t) < 10:
                self.comment(e, 'Beleg zu kurz?')
            elif t[-1] in u'….?!':
                pass
            elif t[-1] == u'«' and t[-2] in '.?!':
                pass
            else:
                self.comment(e, u'Interpunktion am Belegende prüfen')

    def check_for_missing_whitespace(self, element):
        '''
        '''
        for target in (self.TAG['Beleg'], self.TAG['Definition']):

            for e in element.iter(str(target)):

                text = u' '.join((''.join(et.ETXPath('.//text()')(e))).split())
                bib_ref = u' '.join((''.join(et.ETXPath('.//%(Fundstelle)s//text()' % self.TAG)(e))).split())

                # no check within //Fundstelle and we also ignore explicitly
                # given exceptions
                text = text.replace(bib_ref, '')
                # normalize and filter out special and rare wordforms
                text = ' '.join([t for t in text.split() if t not in TOKENIZATION_EXCEPTIONS])

                for index, char2 in enumerate(text[1:]):

                    char1 = text[index]
                    class1 = unicodedata.category(char1)
                    class2 = unicodedata.category(char2)

                    if class2 == 'Lu' and class1 in ('Ll', 'Pe', 'Po', ) and not char1 == '/':
                        # e.g. aB ,A )A -A
                        self.comment(e, u'fehlender Weißraum:%s%s' % (char1, char2))
                    elif class2 == 'Ps' and class1 in ('Lu', 'Ll', 'Po', 'Pd', ):
                        # e.g. a( A( .( -(
                        self.comment(e, u'fehlender Weißraum:%s%s' % (char1, char2))
                    elif char1 == u'«' and not (char2 in self._WHITESPACE or class2 in ('Pe', 'Po')):
                        self.comment(e, u'fehlender Weißraum:%s%s' % (char1, char2))
                    elif char1 in u'»(/' and char2 in self._WHITESPACE:
                        self.comment(e, u'unnötiger Weißraum:%s%s' % (char1, char2))
                    elif char2 == u'»' and not (char1 in self._WHITESPACE or class1 == 'Ps'):
                        self.comment(e, u'fehlender Weißraum:%s%s' % (char1, char2))
                    elif class2 == 'Po' and char2 not in u'%&*†/…' and char1 in self._WHITESPACE:
                        self.comment(e, u'unnötiger Weißraum:%s%s' % (char1, char2))

    def check_balanced_characters(self, article):
        for target in (self.TAG['Definition'], self.TAG['Beleg'], self.TAG['Kompetenzbeispiel'], self.TAG['Kollokation1'], self.TAG['Kollokation2']):
            for e in article.iter(str(target)):
                text = ''.join(et.ETXPath('.//text()')(e))
                text = ' '.join([t for t in text.split() if t not in self.PARENTESIS_EXCEPTIONS])
                balanced_chars = ''.join([char for char in text if char in u'»«()[]'])
                length = 0
                while length != len(balanced_chars):
                    length = len(balanced_chars)
                    balanced_chars = balanced_chars.replace(u'»«', '')
                    balanced_chars = balanced_chars.replace(u'()', '')
                    balanced_chars = balanced_chars.replace(u'[]', '')
                if len(balanced_chars) != 0:
                    self.comment(e, 'Paarzeichenfehler:%s' % balanced_chars)


if __name__ == '__main__':

    typographer = TypographyChecker()
    restructurer = StructureChecker()
