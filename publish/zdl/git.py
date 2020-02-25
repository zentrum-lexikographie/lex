import git

from zdl import logger


class Dictionary(git.Repo):

    def __init__(self, repo_dir,
                 server_branch='zdl-lex-server/production',
                 local_branch=None):
        super(Dictionary, self).__init__(repo_dir)
        self.server_branch = server_branch
        self.fetch()

    def fetch(self):
        self.remotes.origin.fetch()
        assert self.server_branch in self.remotes.origin.refs

    def rebase_on_remote(self):
        self.remotes.origin.fetch()
        local = self.repo.head.reference
        remote = self.repo.remotes.origin.refs[self.remote_branch]
        try:
            logger.info('%s: Rebasing %s on %s', self, local, remote)
            self.repo.git.rebase(remote)
        except git.exc.GitCommandError as e:
            logger.exception(
                '%s: Rebasing on %s failed; aborting', self, remote
            )
            self.repo.git.rebase(abort=True)
            raise RebaseFailed from e

