from pathlib import Path

import git

from zdl.cli import logger


class Workspace:
    def __init__(self, git_origin, git_branch, home, name):
        self.origin = git_origin
        self.remote_branch = git_branch
        self.local_branch = '/'.join(['workspace', name])
        self.dir = (home / 'workspace' / name).resolve()

    def fetch(self):
        logger.info('Fetching from %s', self.origin)
        self.repo.remotes.origin.fetch()

    def reset_to_remote(self):
        dir_posix = self.dir.as_posix()
        if self.dir.is_dir():
            self.repo = git.Repo(dir_posix)
        else:
            self.repo = git.Repo.init(dir_posix)

        if self.repo.is_dirty():
            raise Exception('Workspace repository is dirty.')

        if len(self.repo.untracked_files) > 0:
            raise Exception('Workspace repository contains untracked files.')

        logger.info('Workspace repository: %s', self.repo)
        if 'origin' not in self.repo.remotes:
            origin = self.repo.create_remote('origin', self.origin)
        else:
            origin = self.repo.remotes.origin

        self.fetch()

        logger.info(
            'Setting up %s and %s', self.remote_branch, self.local_branch
        )
        assert (self.remote_branch in origin.refs)
        if self.remote_branch not in self.repo.heads:
            ref = origin.refs[self.remote_branch]
            head = self.repo.create_head(self.remote_branch, ref)
            head.set_tracking_branch(ref)

        if self.local_branch not in self.repo.heads:
            self.repo.create_head(
                self.local_branch, self.repo.heads[self.remote_branch]
            )

        logger.info('Resetting to %s', self.local_branch)
        self.repo.head.reference = self.repo.heads[self.local_branch]
        self.repo.head.reset(index=True, working_tree=True)

        return self

    def rebase_on_remote(self):
        self.fetch()
        try:
            logger.info(
                'Rebasing %s on %s', self.local_branch, self.remote_branch
            )
            self.repo.git.rebase(self.remote_branch)
        except git.exc.GitCommandError:
            logger.exception(
                'Rebasing on %s failed; aborting', self.remote_branch
            )
            self.repo.git.rebase(abort=True)

    def push_to_remote(self):
        logger.info('Pushing %s to %s', self.local_branch, self.origin)
        if self.local_branch in self.repo.remotes.origin.refs:
            self.repo.git.push('origin', self.local_branch, delete=True)
        self.repo.git.push('origin', self.local_branch)


if __name__ == '__main__':
    try:
        ws = Workspace(
            'file:///home/gregor/repositories/zdl-lex-data.git',
            'zdl-lex-server/textmaschine',
            Path('/home/gregor/repositories/zdl-lex/data'),
            'qa'
        )

        ws.reset_to_remote()
        ws.rebase_on_remote()
        ws.push_to_remote()
    except Exception:
        logger.exception('Error in workspace-based workflow')
