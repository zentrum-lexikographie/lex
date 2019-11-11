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


def parser(strip=False):
    return _stripping_xml_parser if strip else _xml_parser


_article_els = xpath('//d:Artikel')


def get_articles(document):
    for article in _article_els(document):
        yield (document, article)


_xml_prolog = '<?xml version="1.0" encoding="UTF-8"?>'


def fromstring(s, strip=False):
    if s.startswith(_xml_prolog):
        s = s[len(_xml_prolog):].lstrip()
    return get_articles(et.fromstring(s, parser(strip)))


def parse(p, strip=False):
    with p.open() as f:
        return get_articles(et.parse(f, parser(strip)))


def tostring(document):
    return '\n'.join([
        _xml_prolog,
        et.tostring(document, encoding=str, xml_declaration=False)
    ])


def save(document, p):
    p.write_text(tostring(document), encoding='utf-8')


_surface_form_els = xpath('.//d:Formangabe/d:Schreibung')
_grammar_qn = qname('d', 'Grammatik')
_pos_els = xpath('./d:Wortklasse')
_genus_els = xpath('./d:Genus')
_xml_id_qn = qname('xml', 'id')


def metadata(article):
    xml_id = article.get(str(_xml_id_qn))
    typ = article.get('Typ', '')
    author = article.get('Autor', '')
    status = article.get('Status', '')
    source = article.get('Quelle', '')
    for sf in _surface_form_els(article):
        name = text(sf)
        for gr in sf.itersiblings(str(_grammar_qn)):
            yield {'name': name,
                   'hidx': sf.get('hidx', ''),
                   'pos': ''.join(map(text, _pos_els(gr))),
                   'gen': ''.join(map(text, _genus_els(gr))),
                   'type': typ,
                   'author': author,
                   'status': status,
                   'source': source,
                   'id': xml_id}
            break


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


_template = '''
<DWDS xmlns="http://www.dwds.de/ns/1.0">
    <Artikel Quelle="ZDL" Status="Artikelrumpf" Typ="Minimalartikel" xml:id="id" Zeitstempel="1970-01-01" Erstfassung="ZDL">
        <Formangabe Typ="Hauptform">
            <Schreibung>[SCHREIBUNG]</Schreibung>
            <Grammatik>
              <Wortklasse>[WORTKLASSE]</Wortklasse>
            </Grammatik>
            <Diasystematik></Diasystematik>
        </Formangabe>
        <Verweise></Verweise>
        <Diachronie class="invisible">
            <Etymologie></Etymologie>
            <Formgeschichte></Formgeschichte>
            <Bedeutungsgeschichte></Bedeutungsgeschichte>
        </Diachronie>
        <Lesart>
            <Syntagmatik></Syntagmatik>
            <Diasystematik></Diasystematik>
            <Verweise></Verweise>
            <Definition Typ="Basis"></Definition>
            <Kollokationen></Kollokationen>
            <Verwendungsbeispiele></Verwendungsbeispiele>
        </Lesart>
    </Artikel>
</DWDS>
'''


def create(source, id, timestamp=None):
    timestamp = timestamp or datetime.datetime.now(pytz.timezone('Europe/Berlin'))
    document, article = next(fromstring(_template))
    article.set('Quelle', source)
    article.set('{http://www.w3.org/XML/1998/namespace}id', id)
    article.set('Zeitstempel', timestamp.strftime('%Y-%m-%d'))
    return document
