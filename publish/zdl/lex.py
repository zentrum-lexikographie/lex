import re
import unicodedata
import requests
import uuid

from random import randrange
from urllib.parse import urljoin, quote

import zdl.article


def json(r):
    r.raise_for_status()
    return r.json()


class Server:
    def __init__(self, base_url='https://lex.dwds.de/', http_auth=None):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.auth = http_auth
        self.token = str(uuid.uuid1())

    def status(self):
        return json(self.session.get(urljoin(self.base_url, '/status')))

    def index(self, query, limit=10):
        return json(self.session.get(
            urljoin(self.base_url, '/index'),
            params={'q': query, 'limit': limit}
        ))

    def get_article(self, id):
        r = self.session.get(
            urljoin(self.base_url, '/'.join(['/article', quote(id)]))
        )
        r.raise_for_status()
        r.encoding = 'utf-8'
        return zdl.article.fromstring(r.text)

    def post_article(self, id, document):
        r = self.session.post(
            urljoin(self.base_url, '/'.join(['/article', quote(id)])),
            data=zdl.article.tostring(document).encode('utf-8')
        )
        r.raise_for_status()
        return zdl.article.fromstring(r.text)

    def locks(self):
        return json(self.session.get(
            urljoin(self.base_url, '/lock')
        ))

    def acquire_lock(self, id, seconds):
        return json(self.session.post(
            urljoin(self.base_url, '/'.join(['/lock', quote(id)])),
            params={'token': self.token, 'ttl': seconds}
        ))

    def release_lock(self, id):
        return json(self.session.delete(
            urljoin(self.base_url, '/'.join(['/lock', quote(id)])),
            params={'token': self.token}
        ))

    def git_commit(self):
        return json(self.session.patch(
            urljoin(self.base_url, '/git')
        ))

    def git_rebase(self, object_id):
        return json(self.session.post(
            urljoin(urljoin(self.base_url, '/git/ff/'), object_id)
        ))


def _strip_accents(s):
    return ''.join(c for c in unicodedata.normalize('NFD', s)
                   if unicodedata.category(c) != 'Mn')


def get_filename(form):
    form = form.replace('ß', 'ss')
    form = _strip_accents(form)
    form = form.replace(' ', '_')
    form = re.sub(r'[^\w\d\-_]', '_', form)
    return form


def generate_id(lex_server):
    for _ in range(100):
        candidate = ''.join(['E_', str(randrange(0, 10000000))])
        id_query = lex_server.index(('id:*%s*' % candidate), limit=0)
        exists = id_query.get('total') > 0
        if not exists:
            return candidate
    raise Exception('No unique ID found after 100 random attempts')
