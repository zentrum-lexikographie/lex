#! /usr/bin/env python
#encoding: utf-8

import datetime, pytz
import lxml.etree as ET

def add_comment(element, comment, author='automatically inserted'):
    start = ET.ProcessingInstruction('oxy_comment_start')
    end = ET.ProcessingInstruction('oxy_comment_end')
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
