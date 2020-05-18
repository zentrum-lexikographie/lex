# Workflow for Contributing to `zdl-lex-server/production`

The server component of the lexicographic workbench uses
[git](https://git-scm.com/) as a versioned data store for lexicon articles.
Lexicographers edit such articles in XML format via a customized version of
[Oxygen XML Editor](https://www.oxygenxml.com/), post changes via HTTP to the
server component where they get saved to the server file system and subsequently
are committed to a git repository in regular intervals. As the versioning of
articles is opaque to the lexicographers, i. e. they have no direct access to
the version control system via the editing environment, the change history has
to be linear from their perspective. Merging parallel and potentially
conflicting lines of work from other sources (bulk updates from technical staff
for example) cannot be achieved via the lexicographer's data access path; it is
also not desired given the potential complexity of this task.

To facilitate parallel work on the lexicographic articles (data set), a workflow
has to be established, which assigns responsibility for merging changes to
contributors with more flexible data access paths and provides for means of
synchronizing concurrent lines of work. The following step-by-step guide
describes such a workflow.

## Creating Feature Branches

All lexicon articles are versioned and stored via the git repository
`https://git.zdl.org/zdl/wb`. The server component (`zdl-lex-server`) commits
contributions to this repository in its own branch `zdl-lex-server/production`
which (for the aforementioned reasons) represents a linear history of the
articles development. Publications of the data set, i.e. on the DWDS' site, are
based on revisions of this branch.

Any parallel work on the data set which should be integrated with this main-line
branch eventually should take place in feature branches that have their base in
revisions of `zdl-lex-server/production`. For further information on the notion
of feature branches, see [Vincent Driessen's branching
model](https://nvie.com/posts/a-successful-git-branching-model/).

To create a feature branch based on the server's main branch, do the following
in a clone of the `zdl/wb` repository:

```plaintext
$ git fetch
$ git checkout -b xhrld/audio-update-202005 --no-track origin/zdl-lex-server/production
```

The first command fetches the most recent revisions from the origin repository;
the second checks out a new branch based on `zdl-lex-server`'s line of work.
Feature branches should follow a naming convention which ensures that any other
contributor can easily derive the purpose of the branch and its author from its
name, assuming that feature branches are normally private with only one
contributor.

Work on the feature branch is committed and pushed to the origin repository so
that it can be reviewed by others and eventually integrated with the server
branch.

```plaintext
$ git commit -a -m 'New audio files added'
$ git push
```

## Rebasing Feature Branches on the Main Branch

While working on a feature branch, work on the main branch continues as well. As
the main branch's work should take priority over any feature developments, the
latter should be prepared for integration by rebasing. Rebasing means that
changes on the feature branch are tracked back to the common ancestor of the
feature and the main branch, and then reapplied to the current tip/head of the
main branch.

```plaintext
$ git fetch
$ git rebase origin/zdl-lex-server/production
```

The advantage of the rebase is two-fold:

1. Rebasing the feature branch creates a linear history of the main branch
   commits and additional commits from the feature branch, which means that
   integration of the feature branch with the main-line development can be
   achieved by fast-forwarding the main branch to the feature branch's head.
1. Any conflicts between the main and feature branch have to be resolved during
   the rebase, thus assigning the responsibility for this task to feature branch
   authors.
   
For large-scale changes (schema adjustments, bulk updates etc.), it is
recommended to script/automate these changes, so they can be reproduced without
additional effort on an updated state of the data set. As conflicts are more
likely in such cases, automating the changes offers the option to simply reset
the feature branch to the current main branch and reapply the changes, thereby
avoiding the need to tediously solve conflicts.

```plaintext
$ git fetch
$ git reset --hard origin/zdl-lex-server/production
$ … # reapply changes
```

