import logging
import sys
from os import environ

from gitlab import Gitlab

log_level = logging.DEBUG if environ.get("DEBUG", "") else logging.INFO

logging.basicConfig(
    level=log_level,
    format="[%(asctime)s] %(levelname)7s – %(name)s : %(message)s",
    handlers=(logging.StreamHandler(stream=sys.stderr),),
)

logger = logging.getLogger("zdl_lex")

gitup_url = environ.get("ZDL_LEX_GITUP_URL", "https://gitup.uni-potsdam.de")
dwdswb_project_id = int(environ.get("ZDL_LEX_GITUP_DWDSWB_PROJECT_ID", "21451"))
lex_project_id = int(environ.get("ZDL_LEX_GITUP_LEX_PROJECT_ID", "21675"))

job_token = environ.get("CI_JOB_TOKEN")
dwdswb_token = environ.get("ZDL_LEX_GITUP_DWDSWB_TOKEN")
private_token = environ.get("ZDL_LEX_GITUP_PAT")


def gitup(private_token=None, job_token=None):
    return Gitlab(gitup_url, private_token=private_token, job_token=job_token)


def project(id, private_token=None):
    api = gitup(
        private_token=private_token,
        job_token=(job_token if not private_token else None),
    )
    return api.projects.get(id, lazy=True)


def dwdswb_project():
    return project(dwdswb_project_id, dwdswb_token)


def lex_project():
    return project(lex_project_id, private_token)
