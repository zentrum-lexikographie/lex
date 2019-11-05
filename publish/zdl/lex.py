from urllib.parse import urljoin

import requests


def json(r):
    r.raise_for_status()
    return r.json()


class Server:
    def __init__(self, base_url='https://lex.dwds.de/', http_auth=None):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.auth = http_auth

    def status(self):
        return json(self.session.get(urljoin(self.base_url, '/status')))

    def index(self, query, limit=10):
        return json(self.session.get(
            urljoin(self.base_url, '/index'),
            params={'q': query, 'limit': limit}
        ))

    def locks(self):
        return json(self.session.get(urljoin(self.base_url, '/lock')))

    def git_commit(self):
        return json(self.session.patch(
            urljoin(self.base_url, '/git')
        ))

    def git_rebase(self, object_id):
        return json(self.session.post(
            urljoin(urljoin(self.base_url, '/git/ff/'), object_id)
        ))
