#! /usr/bin/env python

import datetime, pytz
import lxml.etree as et

def add_comment(element, comment, author='automatically inserted'):
    start = et.ProcessingInstruction('oxy_comment_start')
    end = et.ProcessingInstruction('oxy_comment_end')
    start.text = 'author="%s" timestamp="%s" comment="%s"' % (
            author,
            datetime.datetime.now(pytz.utc).strftime('%Y%m%dT%H%M%S+0000'),
            comment
    )
    parent = element.getparent()
    if parent is not None:
        index = parent.index(element)
        parent.insert(index, start)
        parent.append(end)
        parent.insert(index+2, end)
    else:
        raise NotImplementedError
