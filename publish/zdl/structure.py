import re
import lxml.etree as et
from .article import xpath, qname, text, tail, is_pi

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

_oxy_comment_pi_targets = set(["oxy_comment_start", "oxy_comment_end"])


def reposition_pis(root):
    '''Move all PIs (i.e. oXygen comments) to canonical places.

    This is done to have //Belegtext as mixed content
    with at most on level of structuring. Even though in principle
    it is possible to mark spans of text with a comment in oXygen,
    at this phase of the lexicographical process we allow only
    the most general comments to stay in the sources.
    Those general comments are believed to focus on bigger units
    than spans of texts.'''
    for node in root.iter():
        if not is_pi(node):
            continue
        if node.target not in _oxy_comment_pi_targets:
            continue

        parent = node.getparent()
        if parent.tag in _canonical_pi_locations:
            continue

        # when moving PIs, don't forget about the tail
        pi_tail = tail(node, strip=False)
        if pi_tail != '':
            previous = node.getprevious()
            if previous is None:
                parent.text = text(parent, strip=False) + pi_tail
            else:
                previous.tail = tail(previous, strip=False) + pi_tail
            node.tail = None

        # find target position
        for parent in node.iterancestors():
            if parent.tag not in _canonical_pi_locations:
                continue
            if node.target == 'oxy_comment_start':
                gp = parent.getparent()
                gp.insert(gp.index(parent), node)
            elif node.target == 'oxy_comment_end':
                gp = parent.getparent()
                gp.insert(gp.index(parent) + 1, node)
            break
    return []


def remove_redaction_pis(root):
    for node in root.iter():
        if not is_pi(node) or not node.target == 'oxy_comment_start':
            continue
        if not node.attrib.get('author') == 'Redaktion2':
            continue
        level = 0
        for end in node.itersiblings():
            if not is_pi(end):
                continue
            if end.target == 'oxy_comment_start':
                level += 1
            elif end.target == 'oxy_comment_end':
                if level == 0:
                    node.getparent().remove(node)
                    end.getparent().remove(end)
                    break
                else:
                    level -= 1
    return []


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


def check(article):
    comments = []

    comments.extend(reposition_pis(article))
    comments.extend(remove_redaction_pis(article))
    comments.extend(expand_grammatical_atoms(article))
    comments.extend(insert_n_markers(article))

    return comments
