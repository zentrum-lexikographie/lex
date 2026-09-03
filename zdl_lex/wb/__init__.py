import gzip
import tarfile
from functools import cache
from io import BytesIO
from os import environ
from pathlib import Path
from tempfile import NamedTemporaryFile

import requests
from gitlab import Gitlab
from lxml import etree as ET

ns = "http://www.dwds.de/ns/1.0"
ns_mapping = {"d": ns}
xml_parser = ET.XMLParser(remove_pis=True, remove_comments=True)


def xml_doc(f):
    return ET.parse(f, xml_parser)


@cache
def qn(ln):
    return f"{{{ns}}}{ln}"


def xpath(tree, expr):
    return tree.xpath(expr, namespaces=ns_mapping)


def articles(xml_doc):
    return xpath(xml_doc, "/d:DWDS/d:Artikel")


xml_id = "{http://www.w3.org/XML/1998/namespace}id"


def rename_xml_id_attr(el):
    if xml_id in el.attrib:
        if "id" not in el.attrib:
            el.attrib["id"] = el.attrib[xml_id]
        del el.attrib[xml_id]


env_gitup_url = environ.get("ZDL_LEX_GITUP_URL", "https://gitup.uni-potsdam.de")
env_project_id = int(environ.get("ZDL_LEX_GITUP_DWDSWB_PROJECT_ID", "21451"))
env_private_token = environ.get("ZDL_LEX_GITUP_DWDSWB_TOKEN")
env_job_token = environ.get("CI_JOB_TOKEN") if env_private_token is None else None


def git(sha=None, all=False, raw_data=False):
    url = f"{env_gitup_url}/api/v4/projects/{env_project_id}/repository/archive"
    params = {"sha": sha} if sha else {}
    headers = {"PRIVATE-TOKEN": env_private_token}
    r = requests.get(url, params=params, headers=headers, stream=True)
    r.raise_for_status()
    temp_archive_file = None
    try:
        with NamedTemporaryFile("wb", delete=False) as taf:
            temp_archive_file = taf.name
            for chunk in r.iter_content(chunk_size=8192):
                taf.write(chunk)
        with tarfile.open(temp_archive_file) as tf:
            for ti in tf:
                if ti.isreg() and ti.name.endswith(".xml"):
                    xml_file_id = str(Path(*Path(ti.name).parts[1:]))
                    for article in articles(xml_doc(tf.extractfile(ti))):
                        # skip articles in the making
                        if not all and article.attrib.get("Status", "") != "Red-f":
                            continue
                        # remove raw NLP data (corpus examples)
                        if not raw_data:
                            for raw_data_el in xpath(article, ".//d:Rohdaten"):
                                raw_data_el.getparent().remove(raw_data_el)
                        # rename @xml:id attributes to @id (avoiding clashes)
                        rename_xml_id_attr(article)
                        # overwrite article's @id with file path
                        article.attrib["id"] = xml_file_id
                        for el in article.iterdescendants():
                            rename_xml_id_attr(el)
                        yield article
    finally:
        if temp_archive_file is not None:
            Path(temp_archive_file).unlink()


def project():
    gitup = Gitlab(
        env_gitup_url, private_token=env_private_token, job_token=env_job_token
    )
    return gitup.projects.get(env_project_id, lazy=True)


def tags():
    tags = project().tags.list(get_all=True)
    tags = (t.get_id() for t in tags)
    tags = sorted(tags, reverse=True)
    return tags


def versions():
    packages = project().packages.list(package_type="generic", get_all=True)
    versions = (
        p.attributes["version"] for p in packages if p.attributes["name"] == "dwdswb"
    )
    versions = sorted(versions, reverse=True)
    return versions


def upload(version, path):
    project().generic_packages.upload(
        package_name="dwdswb",
        package_version=version,
        file_name="dwdswb.xml.gz",
        path=path,
    )


def download(version=None):
    if version is None:
        version, *_ = versions()
    content = project().generic_packages.download(
        package_name="dwdswb", package_version=version, file_name="dwdswb.xml.gz"
    )
    with gzip.open(BytesIO(content)) as gf:
        yield from articles(xml_doc(gf))
