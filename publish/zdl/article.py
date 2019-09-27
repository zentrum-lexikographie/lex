import datetime, pytz
import lxml.etree as et


_namespaces = {'d': 'http://www.dwds.de/ns/1.0',
               'tei': 'http://www.tei-c.org/ns/1.0',
               'xml': 'http://www.w3.org/XML/1998/namespace'}


def xpath(expr):
    return et.XPath(expr, namespaces=_namespaces)


def qname(ns, ln):
    return et.QName('{%s}%s' % (_namespaces.get(ns, ''), ln))


def content(s, default='', strip=True):
    s = s or default
    s = s.strip() if strip and s is not None else s
    return s


def text(el, default='', strip=True):
    return content(el.text, default, strip) if el is not None else default


def tail(el, default='', strip=True):
    return content(el.tail, default, strip) if el is not None else default


def normalize_text(s):
    return s if s and len(s) > 0 else None


def is_pi(node):
    return isinstance(node, et._ProcessingInstruction)


_text_nodes = xpath('.//text()')


def el_text(el):
    return ''.join(_text_nodes(el))


_file_filter = set(["indexedvalues.xml", "__contents__.xml"])


def files(articles_dir):
    for f in articles_dir.glob('**/*.xml'):
        if f.name not in _file_filter:
            yield f


_xml_parser = et.XMLParser(
    encoding='utf8',
    remove_comments=True,
    remove_pis=False,
)

_stripping_xml_parser = et.XMLParser(
    encoding='utf8',
    remove_comments=True,
    remove_pis=True,
)

_article_els = xpath('//d:Artikel')


def parse(p, strip=False):
    with p.open() as f:
        parser = _stripping_xml_parser if strip else _xml_parser
        document = et.parse(f, parser)
        for article in _article_els(document):
            yield (document, article)


def has_status(status, article):
    return article.get('Status') == status


def _escape_quotes(s):
    return s.replace('"', '&quot;')


def add_comment(element, comment, author, timestamp=None):
    timestamp = timestamp or datetime.datetime.now(pytz.utc)
    start = et.ProcessingInstruction('oxy_comment_start')
    end = et.ProcessingInstruction('oxy_comment_end')
    start.text = 'author="%s" timestamp="%s" comment="%s"' % (
        _escape_quotes(author),
        timestamp.strftime('%Y%m%dT%H%M%S+0000'),
        _escape_quotes(comment)
    )
    parent = element.getparent()
    if parent is not None:
        index = parent.index(element)
        parent.insert(index, start)
        parent.append(end)
        parent.insert(index + 2, end)
    else:
        raise NotImplementedError


def save(document, p):
    p.write_text('\n'.join([
        '<?xml version="1.0" encoding="UTF-8"?>',
        et.tostring(document, encoding=str, xml_declaration=False)
    ]), encoding='utf-8')
