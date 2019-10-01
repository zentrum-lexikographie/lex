from zdl.article import xpath, qname, text


def _recommended_only_on_full_entries(article):
    if article.get('Empfehlung') != 'ja':
        return []
    if article.get('Status') == 'Red-f' and article.get('Typ') == 'Vollartikel':
        return []
    return [(article, '@Empfehlung only on published full entries')]


_sample_text_els = xpath('.//d:Belegtext')


def _has_illegal_separators(node):
    if '/' in text(node):
        return True
    for el in _sample_text_els(node):
        if ';' in text(el):
            return True
    return False


_collocation_qn = qname('d', 'Kollokation')
_collocation1_qn = qname('d', 'Kollokation1')
_collocation2_qn = qname('d', 'Kollokation2')
_construction_pattern_qn = qname('d', 'Konstruktionsmuster')
_phrase_qn = qname('d', 'Phrasem')


def _illegal_separators(article):
    comments = []
    for context in (_collocation_qn, _collocation1_qn, _collocation2_qn,
                    _construction_pattern_qn, _phrase_qn):
        for el in article.iter(str(context)):
            if _has_illegal_separators(el):
                comments.append((el, 'no illegal separators in %s' % context))
    return comments


_diachrony_els = xpath('.//d:Diachronie')


def _illegal_markup(article):
    comments = []
    for el in _diachrony_els(article):
        txt = text(el)
        legal = True
        for char in '\'"‘’':
            if char in txt:
                legal = False
                break
        if not legal:
            comments.append((el, 'no illegal mark-up in //Diachronie'))
    return comments


_surface_form_els = xpath('.//d:Formangabe')
_diasystem_els = xpath('.//d:Diasystematik/*')
_frequency_els = xpath('.//d:Formangabe/d:Frequenzangabe')


def _single_surface_form_constraints(article):
    surface_forms = _surface_form_els(article)
    if len(surface_forms) != 1:
        return []

    el = surface_forms[0]
    comments = []
    if len(_diasystem_els(article)) > 0:
        comments.append((el, 'no //Diasystematik/* on single /Formangabe'))
    if len(_frequency_els(article)) > 0:
        comments.append((el, 'no //Frequenzangabe on single /Formangabe'))
    return comments


def check(article):
    comments = []
    comments.extend(_recommended_only_on_full_entries(article))
    comments.extend(_illegal_separators(article))
    comments.extend(_illegal_markup(article))
    comments.extend(_single_surface_form_constraints(article))
    return comments
