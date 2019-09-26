import re
import lxml.etree as et
from .article import xpath, qname, text

_xml_space_els = xpath('//*[@xml:space]')
_xml_space_qn = qname('xml', 'space')


def remove_xml_space_preserve(root):
    for el in _xml_space_els(root):
        el.attrib.pop(_xml_space_qn)
    return []


_canonical_pi_locations = (
    qname('d', 'DWDS'),
    qname('d', 'Artikel'),
    qname('d', 'Formangabe'),
    qname('d', 'Verweise'),
    qname('d', 'Diasystematik'),
    qname('d', 'Lesart'),
    qname('d', 'Definition'),
    qname('d', 'Beleg'),
    qname('d', 'Kompetenzbeispiel'),
    qname('d', 'Kollokation'),
    qname('d', 'Kollokation1'),
    qname('d', 'Kollokation2'))


def reposition_pis(root):
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
                txt = (parent.text or '') + (element.tail or '')
                parent.text = txt if len(txt) > 0 else None
            else:
                txt = (previous.tail or '') + (element.tail or '')
                previous.tail = txt if len(txt) > 0 else None
            element.tail = ''
            # find target position
            while parent.tag not in _canonical_pi_locations:
                current = parent
                parent = current.getparent()
            if element.text:
                # comment_start
                parent.insert(parent.index(current), element)
            else:
                # comment_end
                parent.insert(parent.index(current) + 1, element)
    return []


def remove_stylesheet_pis(element):
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
    return []


def remove_redaction_pis(root):
    for element in root.iter():
        if isinstance(element.tag, str):
            continue
        if 'author="Redaktion2"' not in element.text:
            continue
        parent = element.getparent()
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
    return []


_loeschung_els = xpath('//d:Loeschung')


def remove_unneeded_deletions(element):
    for s in _loeschung_els(element):
        parent = s.getparent()

        if (s.tail or '').strip() == '' and s.getnext() is None:
            parent.remove(s)
        elif s.getprevious() is None and (parent.text or '').strip() == '':
            parent.text = s.tail
            parent.remove(s)
    return []


_link_els = xpath('.//d:Lesart/d:Verweise/d:Verweis')


def hide_semantic_links(element):
    for link in _link_els(element):
        if not link.get('type') in ('Antonym', 'Synonym', 'Assoziation'):
            link.set('class', 'invisible')
    return []


_grammar_els = xpath('.//d:Formangabe/d:Grammatik')
_pos_qn = qname('d', 'Wortklasse')
_sg_qn = qname('d', 'Genitiv')
_pl_qn = qname('d', 'Plural')
_number_preference_qn = qname('d', 'Numeruspraeferenz')
_past_tense_qn = qname('d', 'Praeteritum')
_past_participle_qn = qname('d', 'Partizip_II')


def check_grammatical_info(element):
    comments = []
    for grammar in _grammar_els(element):
        word_class = text(grammar.find(_pos_qn), default='n/a')

        if word_class == 'Substantiv':

            sg_form = text(grammar.find(_sg_qn))
            pl_form = text(grammar.find(_pl_qn))
            number_preference = text(grammar.find(_number_preference_qn))

            # silent compatibility fixes
            if pl_form == 'no_data':
                pl_form.text = ''
            elif pl_form == '-0':
                pl_form.text = '-'
            if sg_form == 'no_data':
                sg_form.text = ''
            elif sg_form == '-0':
                sg_form.text = '-'

            # sanity checks
            # Numeruspraeferenz is set by lexicographers so this is the baseline
            if number_preference == 'nur im Singular':
                if len(pl_form) > 0:
                    comments.append((grammar, 'inkonsistente Flexionsangaben'))
            elif number_preference == 'nur im Plural':
                if len(sg_form) > 0:
                    comments.append((grammar, 'inkonsistente Flexionsangaben'))
            elif (sg_form == '') or (pl_form == ''):
                # all forms have to be specified
                comments.append((grammar, 'fehlende Flexionsangaben'))

        elif word_class == 'Verb':

            past_tense = grammar.find(_past_tense_qn)
            past_participle = grammar.find(_past_participle_qn)

            if past_tense is None or past_participle is None:
                comments((grammar, 'unvollständige Flexionsangaben'))

        elif word_class in ('Adjektiv', 'Adverb', 'partizipiales Adjektiv'):
            # TODO: sanity checks
            # preference = grammar.find(qname('d', 'Komparationspraeferenz'))
            # comparative = grammar.find(qname('d', 'Komparativ'))
            # superlative = grammar.find(qname('d', 'Superlativ'))
            pass

        elif word_class == 'Mehrwortausdruck':
            pass

        elif word_class == 'Konjunktion':
            pass

        elif word_class == 'Präposition':
            pass

        else:
            comments.append((
                grammar,
                'fehlende oder unbekannte Wortklasse: "%s"' % word_class
            ))
    return comments


_genitive_els = xpath('.//d:Genitiv')
_genitive_qn = qname('d', 'Genitiv')
_genitive_suffix_re = re.compile(r'\s*-\((?P<optional>e)\)(?P<mandatory>s)\s*')


def expand_grammatical_atoms(article):
    'Silently expand -(e)s and the like to -s and -es in //Genitiv.'
    for g in _genitive_els(article):
        g_match = _genitive_suffix_re.match(g.text)
        if g_match:
            parent = g.getparent()
            new = et.Element(_genitive_qn)
            new.text = '-%(mandatory)s' % g_match.groupdict()
            parent.insert(parent.index(g), new)
            g.text = '-%(optional)s%(mandatory)s' % g_match.groupdict()
    return []


_sense_els = xpath('.//d:Lesart')
_marked_sense_els = xpath('.//d:Lesart[@n]')

_arabic_numeral_markers = ['%i.' % x for x in range(1, 30)]
_latin_small_letter_markers = ['%s)' % x for x in 'abcdefghijklmnopqrstuvwxyz']


def _generate_n_markers(senses, markers):
    if len(senses) > 1:
        for index, sense in enumerate(senses):
            sense.set('n', markers[index])


def insert_n_markers(element):
    'Insert //Lesart@n attributes if there are no such attributes already.'
    marked_senses = list(_marked_sense_els(element))
    if len(marked_senses) > 0:
        return [(marked_senses[0], 'check @n on senses!')]
    else:
        level_1_senses = list(_sense_els(element))
        _generate_n_markers(level_1_senses, _arabic_numeral_markers)
        for sense in level_1_senses:
            level_2_senses = list(_sense_els(sense))
            _generate_n_markers(level_2_senses, _latin_small_letter_markers)
        return []


_surface_form_els = xpath('.//d:Lesart/d:Formangabe/d:Schreibung')


def rigid_markup(element, author):
    'There is a more rigid schema on the production server.'
    if 'Autor' not in element.keys():
        element.set('Autor', author)

    for s in _surface_form_els(element):
        if (s.text or '') == '':
            s.getparent().remove(s)
    return []


def check(article):
    comments = []

    comments.extend(remove_xml_space_preserve(article))
    comments.extend(remove_stylesheet_pis(article))
    comments.extend(reposition_pis(article))
    comments.extend(remove_redaction_pis(article))
    comments.extend(remove_unneeded_deletions(article))
    comments.extend(expand_grammatical_atoms(article))
    comments.extend(check_grammatical_info(article))
    # comments.extend(hide_semantic_links(article))
    comments.extend(insert_n_markers(article))
    # comments.extend(rigid_markup(article))

    return comments
