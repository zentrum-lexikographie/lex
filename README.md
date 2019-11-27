# ZDL/Lex – Lexikographic Workbench

## Build Requirements

* [Java 8](https://packages.debian.org/search?keywords=openjdk-8-jdk)
* [Python 3](https://www.python.org/)
* [Docker](https://www.docker.com/)

Java 8 is needed, as Oxygen XML does not work with newer versions.

For Python 3, it is recommended to use a project-specific virtual environment,
e.g. via [pyenv](https://github.com/pyenv/pyenv):

```plaintext
$ pyenv virtualenv zdl-lex
$ pyenv local zdl-lex
```

## Setup

Install Python dependencies and local modules.

```plaintext
$ pip install -r requirements.txt
```

## Build and Deploy

```plaintext
$ zdl-lex-build
Usage: zdl-lex-build [OPTIONS] COMMAND1 [ARGS]... [COMMAND2 [ARGS]...]...

  Scripts for building, compiling, packaging ZDL-Lex.

Options:
  --help  Show this message and exit.

Commands:
  build    Compile and package modules.
  clean    Clean compiler output.
  client   Runs the ZDL-Lex client (in Oxygen XML Editor).
  docker   Build Docker images.
  dwdswb   Runs a MySQL Docker container for testing.
  init     Init build.
  release  Tag and push next version.
  schema   Compile RelaxNG/Schematron rules.
  server   Runs the ZDL-Lex server.
  solr     Runs Solr as a local Docker container for testing.
  version  Print current version
```

## License

Copyright © 2019 Berlin-Brandenburgische Akademie der Wissenschaften.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser Public License for more details.

You should have received a copy of the GNU Lesser Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
