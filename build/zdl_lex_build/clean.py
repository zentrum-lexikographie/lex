import logging
import shutil

from zdl_lex_build import project_dir


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
