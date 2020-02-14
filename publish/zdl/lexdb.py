import re
import urllib.parse

import requests


query_url = 'http://kaskade.dwds.de/dstar/%s/lexdb/export.perl'


def query(corpus,
          select='*', frm='lex', where=None,
          groupby=None, orderby=None,
          limit=10, offset=None):

    url = query_url % urllib.parse.quote(corpus)
    data = {
        'fmt': 'json',
        'select': select,
        'from': frm,
        'where': where,
        'groupby': groupby,
        'orderby': orderby,
        'limit': limit,
        'offset': offset
    }
    data = dict(filter(lambda kv: kv[1] is not None, data.items()))
    r = requests.post(url, data)
    r.raise_for_status()
    r = r.json()
    names = r['names']
    return {
        'query': r['sql'],
        'total': r['nrows'],
        'result': list(map(lambda r: dict(zip(names, r)), r['rows']))
    }


def _remove_quotes(s):
    return re.sub('"', '', s)


def frequencies(corpus, lemmata):
    lemmata = list(map(lambda l: '"%s"' % _remove_quotes(l), lemmata))
    r = query(
        corpus,
        select='l, SUM(f) as f',
        where=('l in (%s)' % ', '.join(lemmata)),
        groupby='l',
        limit=len(lemmata)
    )
    return dict(map(lambda r: (r['l'], int(r['f'])), r.get('result', [])))
