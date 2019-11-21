#!/usr/bin/env python

import datetime
from pathlib import Path
import re

from git import Repo

project_dir = (Path(__file__) / '..' / '..').resolve()
repo = Repo(project_dir.as_posix())

_version_tag_re = re.compile(r'v\d{6}\.\d{2}\.\d{2}')


def versions():
    tags = sorted([t.name for t in repo.tags])
    tags.reverse()
    for tag in tags:
        if _version_tag_re.match(tag) is not None:
            yield tag.lstrip('v')


def current_version():
    for version in versions():
        return version
    return "000000.00.00"


def set_next_version():
    if repo.is_dirty():
        raise Exception("Git repository/dir is dirty.")
    next_version = datetime.datetime.now().strftime('%Y%m.%d.%H')
    next_tag = 'v' + next_version
    if next_version in versions():
        raise Exception('%s already exists.' % (next_tag, ))
    repo.create_tag(next_tag)
    return next_version


if __name__ == '__main__':
    print(current_version())
