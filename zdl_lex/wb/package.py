import gzip
import sys
from pathlib import Path
from tempfile import NamedTemporaryFile

from lxml import etree as ET

from zdl_lex.env import logger

from . import git, ns, qn, tags, upload, versions

if __name__ == "__main__":
    current_release, *_ = tags()
    if current_release in versions():
        logger.info(f"DWDSwb v{current_release} already released")
        sys.exit(0)
    logger.info(f"Releasing DWDSwb v{current_release}")
    temp_file_name = None
    try:
        with NamedTemporaryFile("wb", delete=False) as tf:
            temp_file_name = tf.name
        with (
            gzip.open(temp_file_name, mode="wb") as gzf,
            ET.xmlfile(gzf, encoding="utf-8") as xf,
        ):
            xf.write_declaration()
            with xf.element(qn("DWDS"), nsmap={None: ns}):
                for article in git(current_release):
                    xf.write(article)
                    xf.flush()
        upload(current_release, temp_file_name)
    finally:
        if temp_file_name:
            Path(temp_file_name).unlink()
