# Workflow for Contributing to `zdl-lex/production`

The `zdl-lex-server` component of the lexicographic workbench uses
[git](https://git-scm.com/) as a versioned data store for lexicon articles.
Lexicographers edit such articles in XML format via a customized version of
[Oxygen XML Editor](https://www.oxygenxml.com/), post changes via HTTP to the
server component where they get saved to the server filesystem and subsequently
are committed to a git repository in regular intervals. As the versioning of
articles is opaque to the lexicographers, i. e. they have no direct access to
the version control system via the editing environment, the change history has
to be linear from their perspective. Merging parallel and potentially
conflicting lines of work from other sources (bulk updates from technical staff
for example) cannot be achieved via the lexicographer's data access path; it is
also not desired given the potential complexity of this task.

…

```plaintext
$ git status -s | wc -l # soll 0 sein -> keine pending changes
$ git fetch # letzte Änderungen von git.zdl.org ziehen
$ git checkout -b xhrld/audio-update-202004 --no-track origin/zdl-lex-server/production # neuen branch für Audio-Updates erstellen
$ … # Updates lokal durchführen, z. B. per Skript
$ git commit -a -m 'New audio files added' # Commit auf feature branch
$ git push # feature branch veröffentlichen
```
