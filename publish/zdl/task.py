from pathlib import Path

import git

import zdl.article
import zdl.lex

from zdl.cli import logger


class RebaseFailed(Exception):
    """Custom exception when task-related changes cannot be rebased on the
    remote's main branch.
    """
    pass


class Workspace:
    def __init__(
            self,
            git_origin, git_branch,
            git_author_name, git_author_email,
            home, name):
        self.origin = git_origin
        self.remote_branch = git_branch
        self.local_branch = '/'.join(['workspace', name])
        self.git_author = '%s <%s>' % (git_author_name, git_author_email)

        self.dir = (home / 'workspace' / name).resolve()
        dir_posix = self.dir.as_posix()

        if self.dir.is_dir():
            self.repo = git.Repo(dir_posix)
        else:
            self.repo = git.Repo.init(dir_posix)

        if self.repo.is_dirty():
            raise Exception('Workspace repository is dirty.')

        if len(self.repo.untracked_files) > 0:
            raise Exception('Workspace repository contains untracked files.')

        if 'origin' not in self.repo.remotes:
            self.repo.create_remote('origin', self.origin)

        if self.origin != self.repo.remotes.origin.url:
            raise Exception(
                'Git origin mismatch ("%s" != "%s")' %
                (self.repo.remotes.origin.url, self.origin)
            )

    def __str__(self):
        return '<%s>' % self.dir

    def _fetch(self):
        logger.info('%s: Fetching from %s', self, self.origin)
        self.repo.remotes.origin.fetch()

    def reset_to_remote(self):
        self._fetch()
        local = self.local_branch
        remote = self.repo.remotes.origin.refs[self.remote_branch]
        logger.info('%s: Resetting %s to %s', self, local, remote)
        remote.checkout(force=True)
        if local in self.repo.heads:
            self.repo.delete_head(local, force=True)
        self.repo.create_head(local).checkout()

    def commit(self, message):
        untracked = len(self.repo.untracked_files)
        diffs = len(self.repo.index.diff(None))
        changes = untracked + diffs

        if changes > 0:
            logger.info(
                '%s: Committing to %s (%d change(s))',
                self, self.repo.active_branch, changes
            )
            if untracked > 0:
                self.repo.git.add(self.repo.untracked_files)
            self.repo.git.commit(
                all=True, author=self.git_author, message=message
            )

    def rebase_on_remote(self):
        self._fetch()
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

    def push_to_remote(self):
        local = self.repo.head.reference
        remote = self.repo.remotes.origin

        logger.info('%s: Pushing %s to %s', self, local, remote)
        if self.local_branch in self.repo.remotes.origin.refs:
            self.repo.git.push('origin', self.local_branch, delete=True)

        self.repo.git.push('origin', self.local_branch)


class Task:
    def __init__(self, server, workspace, max_attempts=3):
        self.server = server
        self.workspace = workspace
        self.max_attempts = max_attempts

    def __str__(self):
        return '<%s [%s, %s]>' % (
            type(self).__name__, self.server, self.workspace
        )

    def perform(self):
        # a task is retried, if its results cannot be merged with
        # server-side changes
        for attempt in range(self.max_attempts):
            try:
                logger.info('%s: Performing attempt #%d', self, (attempt + 1))
                # make sure, all server-side edits are in the remote repo
                self.server.git_commit()
                # sync with remote
                self.workspace.reset_to_remote()
                # execute task and commit locally
                self.modify_workspace(self.workspace.dir)
                self.workspace.commit(self.commit_message())
                try:
                    # global server-side lock with timeout; users shall not
                    # interfere with rebasing
                    self.server.acquire_lock("", 300)
                    # trigger commit of intermediate changes; together with the
                    # lock, no further writes should happen
                    self.server.git_commit()
                    # the critical section: merge via rebase
                    self.workspace.rebase_on_remote()
                    self.workspace.push_to_remote()
                    # if the rebase was successful, we ask the server to rebase
                    # its dataset on the merged history
                    self.server.git_rebase(
                        self.workspace.repo.head.commit.hexsha
                    )
                finally:
                    # make sure, the global lock is released eagerly
                    self.server.release_lock("")
                # attempt was successful, stop iteration
                break
            except RebaseFailed:
                # the rebasing of the task's changes onto the server-side
                # branch failed: retry!
                pass

    def modify_workspace(self, dir):
        pass

    def commit_message(self):
        return ('Task results from %s' % type(self).__name__)


class SampleTask(Task):
    def modify_workspace(self, dir):
        logger.info('Performing sample task')

        article_files = list(zdl.article.files(dir))

        import random
        random.shuffle(article_files)

        import itertools
        article_files = set(itertools.islice(article_files, 10))

        for article_file in article_files:
            logger.info(article_file)
            for (document, article) in zdl.article.parse(article_file):
                article.set('Status', 'Red-1')
                zdl.article.save(document, article_file)

        zdl.article.save(
            zdl.article.create('Test', 'Test'),
            self.workspace.dir / 'test.xml'
        )

        logger.info('Pausing for 30s')
        import time
        time.sleep(30)


if __name__ == '__main__':
    try:
        SampleTask(
            zdl.lex.Server(
                'http://localhost:3000/',
                ('admin', 'admin')
            ),
            Workspace(
                'file:///home/gregor/repositories/zdl-lex-data.git',
                'zdl-lex-server/textmaschine',
                'Gregor Middell',
                'gregor.middell@bbaw.de',
                Path('/home/gregor/repositories/zdl-lex/data'),
                'qa'
            )
        ).perform()
    except Exception:
        logger.exception('Error in sample task')
