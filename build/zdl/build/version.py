#!/usr/bin/env python

import datetime
import re

from git import Repo

from zdl.build import project_dir

repo = Repo(project_dir.as_posix())

_all_refs = ['refs/tags/*:refs/tags/*', 'refs/heads/*:refs/remotes/origin/*']


def _git_fetch():
    return [r.fetch(_all_refs) for r in repo.remotes]


def _git_push():
    return [r.push(_all_refs) for r in repo.remotes]


_version_tag_re = re.compile(r'v\d{6}\.\d{2}\.\d{2}')


def versions():
    _git_fetch()
    tags = sorted([t.name for t in repo.tags])
    tags.reverse()
    for tag in tags:
        if _version_tag_re.match(tag) is not None:
            yield tag.lstrip('v')


def current_version():
    current_version = '000000.00.00'
    if not repo.is_dirty():
        for version in versions():
            current_version = version
            break
    return current_version


_version_edn = project_dir / 'common' / 'src' / 'version.edn'


def write_version_edn():
    _version_edn.write_text('{:version "%s"}' % (current_version(), ))


def set_next_version():
    if repo.is_dirty():
        raise Exception("Git repository/dir is dirty.")
    next_version = datetime.datetime.now().strftime('%Y%m.%d.%H')
    next_tag = 'v' + next_version
    if next_version in versions():
        raise Exception('%s already exists.' % (next_tag, ))
    repo.create_tag(next_tag)
    _git_push()
    return next_version
