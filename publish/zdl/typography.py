import lxml.etree as et
import unicodedata
from .article import xpath, qname, text_content, text


_expected_abbreviations = set(('etw.', 'jmd.', 'jmds.', 'jmdn.', 'jmdm.', u'o.\u202fä.', u'o.\u202fÄ.', 'usw.', u'z.\u202fB.', 'bzw.')) # U+202F NARROW NO-BREAK SPACE
_definition_els = xpath('//d:Definition')


def check_abbreviations_in_definitions(element):
    comments = []
    for definition in _definition_els(element):
        for token in text_content(definition).split(' \n\r'):
            if token.endswith('.') and token not in _expected_abbreviations:
                comments.append((definition, 'nicht erlaubte Abkürzung'))
    return comments


def strip_and_correct_whitespace(element):
    'Silently strip and correct whitespace anomalies.'
    for node in element.iter():
        if not isinstance(node, et._Element) or len(node) > 0:
            continue

        txt = text(node, strip=False)
        if txt == '':
            continue

        left = node.getprevious()
        parent = node.getparent()
        if txt[0].isspace():
            txt = txt.lstrip()
            if left is not None:
                if left.tail is None or not left.tail[-1].isspace():
                    left.tail = (left.tail or '') + ' '
            elif parent.text is None or not parent.text[-1].isspace():
                parent.text = (parent.text or '') + ' '
        if len(txt) > 0 and txt[-1].isspace():
            txt = txt.rstrip()
            if node.tail is None or not node.tail[-1].isspace():
                node.tail = ' ' + (node.tail or '')
        node.text = txt if len(txt) > 0 else None

    return []


# note: there's no decomposition for ø
_latin_chars = u'abcdefghijklmnopqrstuvwxyzßABCDEFGHIJKLMNOPQRSTUVWXYZÆæĐđŁłŒœØø€£ðʒ'
_greek_chars = u'λθξνόησιϛεϊδωο'
_arabic_figures = u'0123456789'
_diacritics = u'\u0300\u0301\u0302\u0303\u0304\u0306\u0308\u030a\u030b\u030c\u0327\u0328'
_whitespace = u' \n\u200b\u200d'  # ZERO WIDTH SPACE, ZERO WIDTH JOINER
# bare minimum for //Definition et al. (note: RIGHT SINGLE QUOTATION MARK!)
_punctuation1 = u'.,()’-»«'
# slightly extended for text like data (note: SOLIDUS vs. FRACTION SLASH!)
_punctuation2 = u':;?!/⁄–›‹%‰&=§°+†*@¬×·$€⊆±'
# even more extended for //Fundstelle
_punctuation3 = u'[]_~'
_expected_codepoints = {
    'all': _whitespace + _latin_chars + _greek_chars + _arabic_figures + _diacritics + _punctuation1 + _punctuation2 + _punctuation3,
    'phrase level': _whitespace + _arabic_figures + _latin_chars + _diacritics + _punctuation1,
    'text level': _whitespace + _latin_chars + _greek_chars + _arabic_figures + _diacritics + _punctuation1 + _punctuation2,
    'grammar': _whitespace + _latin_chars + _arabic_figures + _diacritics + _punctuation1 + '.-_/ \n'
}


def _check_chars(root, norm):
    comments = []
    txt = unicodedata.normalize('NFKD', text_content(root))
    for char in txt:
        if char not in _expected_codepoints[norm]:
            comments.append((root, 'Zeichenfehler: %s' % repr(char)))
    return comments


_surface_forms_qn = qname('d', 'Formangabe')
_definition_qn = qname('d', 'Definition')
_paraphrase_qn = qname('d', 'Paraphrase')
_sample_text_qn = qname('d', 'Belegtext')
_source_qn = qname('d', 'Fundstelle')


def check_chars(element):
    'Check text with regard to expected characters per element type.'
    comments = []
    for e in element.iter(str(_surface_forms_qn)):
        comments.extend(_check_chars(e, 'grammar'))
    for e in element.iter(str(_definition_qn)):
        comments.extend(_check_chars(e, 'phrase level'))
    for e in element.iter(str(_paraphrase_qn)):
        comments.extend(_check_chars(e, 'phrase level'))
    for e in element.iter(str(_sample_text_qn)):
        comments.extend(_check_chars(e, 'text level'))
    for e in element.iter(str(_source_qn)):
        comments.extend(_check_chars(e, 'all'))
    return comments


_transliterations = {
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

_sample_qn = qname('d', 'Beleg')


def transliterate(element):
    '''
    Recursively scan tree and adjust certain character combinations to
    proper UNICODE.
    '''
    comments = []
    for e in element.iter():
        if not isinstance(e, et._Element):
            continue
        txt = e.text or ''
        tl = e.tail or ''

        for char, subst in _transliterations.items():
            txt = txt.replace(char, subst)
            tl = tl.replace(char, subst)

        e.text = txt if len(txt) > 0 else None
        e.tail = tl if len(tl) > 0 else None

    # for mixed content, more than one pass may be required
    for e in element.iter(str(_sample_qn)):
        txt = text_content(e)
        for t in _transliterations:
            if t in txt:
                comments.append((e, 'Zeichenkombinationsfehler: %s' % t))
        if '..' in txt:
            comments.append((e, 'Zeichenkombinationsfehler: ..'))

    return comments


_source_text_els = xpath('.//d:Beleg/d:Belegtext')


def check_final_punctuation(element):
    comments = []
    for e in _source_text_els(element):
        t = ' '.join(text_content(e)).split()

        if len(t) < 10:
            comments.append((e, 'Beleg zu kurz?'))
        elif t[-1] in '….?!':
            pass
        elif t[-1] == '«' and t[-2] in '.?!':
            pass
        else:
            comments.append((e, 'Interpunktion am Belegende prüfen'))
    return comments


_source_els = xpath('.//d:Fundstelle')


def check_for_missing_whitespace(element):
    comments = []
    for target in (_sample_qn, _definition_qn):
        for e in element.iter(str(target)):
            txt = ' '.join(text_content(e).split())
            bib_ref = ' '.join(''.join(map(text_content, _source_els(e))).split())

            # no check within //Fundstelle and we also ignore explicitly
            # given exceptions
            txt = txt.replace(bib_ref, '')
            # normalize and filter out special and rare wordforms
            txt = ' '.join([t for t in txt.split()])
            for index, char2 in enumerate(txt[1:]):

                char1 = txt[index]
                class1 = unicodedata.category(char1)
                class2 = unicodedata.category(char2)

                if class2 == 'Lu' and class1 in ('Ll', 'Pe', 'Po') and not char1 == '/':
                    # e.g. aB ,A )A -A
                    comments.append((e, u'fehlender Weißraum:%s%s' % (char1, char2)))
                elif class2 == 'Ps' and class1 in ('Lu', 'Ll', 'Po', 'Pd'):
                    # e.g. a( A( .( -(
                    comments.append((e, u'fehlender Weißraum:%s%s' % (char1, char2)))
                elif char1 == u'«' and not (char2 in _whitespace or class2 in ('Pe', 'Po')):
                    comments.append((e, u'fehlender Weißraum:%s%s' % (char1, char2)))
                elif char1 in u'»(/' and char2 in _whitespace:
                    comments.append((e, u'unnötiger Weißraum:%s%s' % (char1, char2)))
                elif char2 == u'»' and not (char1 in _whitespace or class1 == 'Ps'):
                    comments.append((e, u'fehlender Weißraum:%s%s' % (char1, char2)))
                elif class2 == 'Po' and char2 not in u'%&*†/…' and char1 in _whitespace:
                    comments.append((e, u'unnötiger Weißraum:%s%s' % (char1, char2)))
    return comments


_parentesis_exceptions = (
    # enumerations with parenthesis
    'a)', 'b)', 'c)',
    u'\u200b:-)', u'\u200b:)', u'\u200b:-(',
)

_comp_sample_qn = qname('d', 'Kompetenzbeispiel')
_collocation_qn = qname('d', 'Kollokation')


def check_balanced_characters(article):
    comments = []
    targets = (_definition_qn, _sample_qn, _comp_sample_qn,
               _collocation_qn)
    for target in targets:
        for e in article.iter(str(target)):
            txt = text_content(e)
            txt = ' '.join([t for t in txt.split() if t not in _parentesis_exceptions])
            balanced_chars = ''.join([char for char in txt if char in '»«()[]'])
            length = 0
            while length != len(balanced_chars):
                length = len(balanced_chars)
                balanced_chars = balanced_chars.replace('»«', '')
                balanced_chars = balanced_chars.replace('()', '')
                balanced_chars = balanced_chars.replace('[]', '')
            if len(balanced_chars) != 0:
                comments.append((e, 'Paarzeichenfehler:%s' % balanced_chars))
    return comments


def check(article):
    comments = []
    comments.extend(transliterate(article))
    comments.extend(strip_and_correct_whitespace(article))
    comments.extend(check_chars(article))
    comments.extend(check_final_punctuation(article))
    comments.extend(check_for_missing_whitespace(article))
    comments.extend(check_balanced_characters(article))
    return comments
