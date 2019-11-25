import logging
from pathlib import Path
import shutil

project_dir = (Path(__file__) / '..' / '..').resolve()


def classes(name):
    logging.info({'clean': name})
    classes_dir = project_dir / name / 'classes'
    if classes_dir.is_dir():
        shutil.rmtree(classes_dir.as_posix())
    classes_dir.mkdir()


def all_classes():
    # Plugin for OxygenXML Editor and Server component
    for module in ['client', 'server', 'validator']:
        classes(module)
