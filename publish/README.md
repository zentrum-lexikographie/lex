# ZDL/Lex – Python Tools

## Requirements

* [Python 3](https://www.python.org/)
* [ICU](http://site.icu-project.org/)

It is recommended to use a project-specific virtual environment, e.g. via
[pyenv](https://github.com/pyenv/pyenv):

```plaintext
$ pyenv virtualenv zdl-lex
$ pyenv local zdl-lex
```

## Setup

For proper Unicode string collation, we use
[PyICU](https://pypi.org/project/PyICU/) bindings. Please install the
`libicu-dev`, e.g. via

```
$ sudo apt install libicu-dev
```

Then, install Python dependencies:

```plaintext
$ pip install -e .
```

## Publication process

Prior to publication, the dictionary needs to be in globally consistent state. A range of consistency checks is implemented to achieve this.

The publication of the dictionary data is two-staged:
1. internal publication (on Wednesdays) on `zwei.dwds.de`
2. external publication (on Fridays) on `www.dwds.de`

### Consistency checks

Prior to publication, all consistency checks in `bin/`should be run and errors should be corrected. As checks are run on the local directory, a DWDSWB checkout needs to be properly set up. This can be achieved in two ways:
* symlinking a clone of the dictionary to the publication directory,
* or symlinking the `bin/` and `share/` directories and the `Makefile into a clone of the dictionary

Checks with a scope on individual entries (e.g. typography, metadata) are only run on recently modified entries unless called with the `-s all` parameter. Checks with a scope beyond individual entries (e.g. links, homographs) are always run on all entries.

The checks can be run in parallel using the provided `Makefile`. Running the checks via the `Makefile` will ignore all entries that are not in state `Red-f`, i.e. that are not due for publication:
```plaintext
$ make check -j [number_of_parallel_checks]
```

All checks can also be run individually. For more details on each test, run:
```plaintext
$ bin/${CHECK_SKRIPT} -h
```

In case of errors, corrections should be made via Oxygen to ensure no conflicts arise due to ongoing lexicographic editing. Calling the function library `bin/LexServerAPI.py` directly at any time will trigger a commit/push cycle on the server. Changes can then be pulled and tests can be re-run as necessary.

Some errors can be automatically corrected by running:

```plaintext
$ bin/automatic-corrections.py
```

The skript will trigger a commit/push cycle on the server so that the changes can instantly be pulled to the local clone.

### Internal publication

To publish the dictionary internally to `zwei.dwds.de`, run

```plaintext
$ zdl-mysql ${PATH_TO_DICTIONARY} src_pass=${MYSQL_PASSWORD}
```

This will create a dictionary database from scratch without altering any existing dictionary database. To switch to the newly created dictionary database, run

```plaintext
$ make src_pass=${MYSQL_PASSWORD} dwdswb-update
```

The database is connected using the current user name. A backup of the last active database is dumped in `backup/`.

Before the final database switch, a summary of all added and deleted entries is shown for inspection. In case of unexpected entry deletions, you can kill the make process, fix the error and start again.

### External publication

The dictionary is published on `www.dwds.de` by copying the database from `zwei` and then switching to this copy. To publish the dictionary externally, ssh to `www.dwds.de`, then run

```plaintext
$ make -f /home/herold/Makefile src_pass=${MYSQL_PASSWORD_ON_ZWEI} dst_pass=${MYSQL_PASSWORD_ON_WWW} dwdswb-update
```

After external publication, the published version should be tagged.