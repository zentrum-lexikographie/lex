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
$ git checkout -b xlhrld/audio-update-2020-04-30 --no-track origin/zdl-lex-server/production
```

The first command fetches the most recent revisions from the origin repository;
the second checks out a new branch based on `zdl-lex-server`'s line of work.
Feature branches should follow a naming convention which ensures that any other
contributor can easily derive the purpose of the branch and its author from its
name, assuming that feature branches are normally private with only one
contributor.

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

## Publishing Rebased Feature Branches

Work on the feature branch is committed and pushed to the origin repository so
that it can be reviewed by others and eventually integrated with the server
branch.

```plaintext
$ git commit -a -m 'New audio files added'
$ git push --force-with-lease --set-upstream origin xlhrld/audio-update-2020-04-30
```

The option `--force-with-lease` ensures that the remote reference of the feature
branch is updated and no implicit merge of the local and remote feature branches
is trigggered by deviating branch histories as they occur during rebases.

## Synchronizing Main and Feature Branches

Once work on the feature branch is completed, it can be integrated into the main
branch.

As the server commits to the repository in fixed intervals (currently every 15
minutes), we first ensure that all changes from the lexicographers have been
comitted and pushed to the main branch by triggering a server-side commit/push
manually:

```plaintext
$ curl -u… -X PATCH https://lex.dwds.de/git
```

After the server-side state is synchronized with the origin repository, a final
rebase of the feature branch is done:

```plaintext
$ git fetch
$ git rebase origin/zdl-lex-server/production
```

Assuming that no conflicts have arisen, the rebased feature branch is pushed:

```plaintext
$ git push --force-with-lease --set-upstream origin xlhrld/audio-update-2020-04-30
```

In the final step we instruct the server to fast-forward its branch to the head of the feature branch:

```plaintext
$ curl -u… -X POST https://lex.dwds.de/git/ff/$(git rev-parse HEAD)
```

Now the feature branch is integrated into the server's branch
`zdl-lex-server/production`, all changes of the branch are visible to
lexicographers and the feature branch can (optionally) be deleted.

## Locking the Server-side Data Store

There is the potential for a race condition during the described synchronization
process. Should a user of the lexicographic workbench save changes to articles
during the time of the manually triggered commit/push and the fast-forward of
the server's branch, the latter will either fail or the synchronization of the
server-side file system and the origin repository might break, as both could
become out-of-sync.

There are two options to avoid this scenario: Integrating feature branches
during times of inactivity, i. e. in off-peak hours, or locking the server-side
data store while integrating the feature branch.

When integrating without locking, all that has to be ensured is the inactivity
of other users during the required time slot. This can be established by looking
at the set of pending locks on server resources:

```plaintext
$ curl -u… https://lex.dwds.de/lock
```

If this request returns an empty list, no lexicon articles are currently edited
and the integration will very likely proceed successfully. If there are active
locks on resources, the name of lock owner is listed as well, so one can contact
that active user(s) in order to agree on a time slot during which she/he remains
inactive.

_ … TODO: Document lock/unlock of data store …_

```plaintext
$ export TOKEN=$(uuid)
$ curl -u… -X POST https://lex.dwds.de/lock/?token=$TOKEN
```

```plaintext
$ curl -u… -X DELETE https://lex.dwds.de/lock/?token=$TOKEN
```


