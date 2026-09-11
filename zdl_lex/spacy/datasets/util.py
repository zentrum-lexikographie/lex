import tarfile
from pathlib import Path
from tempfile import NamedTemporaryFile
from urllib.request import urlopen
from zipfile import ZipFile

from zdl_lex.env import logger


def read_zip(url, extract):
    logger.info(f"Downloading {url}")
    temp_zip_file = None
    try:
        with NamedTemporaryFile("wb", delete=False) as tf:
            temp_zip_file = tf.name
            with urlopen(url) as f:
                tf.write(f.read())
        with ZipFile(temp_zip_file) as zf:
            return extract(zf)
    finally:
        if temp_zip_file is not None:
            Path(temp_zip_file).unlink()


def read_tgz(url, extract):
    logger.info(f"Downloading {url}")
    temp_tgz_file = None
    try:
        with NamedTemporaryFile("wb", delete=False) as tf:
            temp_tgz_file = tf.name
            with urlopen(url) as f:
                tf.write(f.read())
        with tarfile.open(temp_tgz_file) as zf:
            return extract(zf)
    finally:
        if temp_tgz_file is not None:
            Path(temp_tgz_file).unlink()
