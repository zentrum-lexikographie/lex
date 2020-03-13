from zdl.article import xpath, text


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


def check(article):
    comments = []
    comments.extend(_illegal_markup(article))
    return comments
