import datetime, pytz
import lxml.etree as et


_namespaces = {'d': 'http://www.dwds.de/ns/1.0',
               'tei': 'http://www.tei-c.org/ns/1.0',
               'xml': 'http://www.w3.org/XML/1998/namespace'}


def xpath(expr):
    return et.XPath(expr, namespaces=_namespaces)


def qname(ns, ln):
    return et.QName('{%s}%s' % (_namespaces.get(ns, ''), ln))


def text(el, default='', strip=True):
    text = el.text if el is not None else ''
    text = text.strip() if strip and text is not None else text
    text = default if text is None else text
    return text


_text_nodes = xpath('.//text()')


def el_text(el):
    return ''.join(_text_nodes(el))


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


def add_comment(element, comment, author, timestamp=None):
    timestamp = timestamp or datetime.datetime.now(pytz.utc)
    start = et.ProcessingInstruction('oxy_comment_start')
    end = et.ProcessingInstruction('oxy_comment_end')
    start.text = 'author="%s" timestamp="%s" comment="%s"' % (
        author,
        timestamp.strftime('%Y%m%dT%H%M%S+0000'),
        comment
    )
    parent = element.getparent()
    if parent is not None:
        index = parent.index(element)
        parent.insert(index, start)
        parent.append(end)
        parent.insert(index + 2, end)
    else:
        raise NotImplementedError
