# ZDL/Lex – Lexikographic Workbench

_A client/server application implementing an authoring environment for
lexicographic articles at the [ZDL](https://www.zdl.org/)_

![Schreibtisch eines Philologen by Die.keimzelle / Wikimedia Commons / CC-BY-3.0](https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Schreibtisch_eines_Philologen.jpg/640px-Schreibtisch_eines_Philologen.jpg)

## Prerequisites

* [GNU/Linux](https://www.debian.org/): Development, builds and tests of the
  platform are performed on [Debian GNU/Linux](https://debian.org/) (currently
  v10 “Buster”). While other UNIX-like operating systems (i. e. MacOS) might
  work, they are not supported. The same goes for MS Windows.
* [Clojure](https://clojure.org/): The server component, mediating between the
  [Oxygen-XML-Editor](https://www.oxygenxml.com/)-based client, a Git-based data
  store and a search service, is written Clojure, a LISP dialect. The same goes
  for Oxygen-XML's project-specific extensions.
* [Java (JDK)](https://openjdk.java.net/): Clojure, being a hosted language,
  requires a current Java runtime.
* [Babashka](https://babashka.org/): The build process is orchestrated via `bb`,
  a “fast native Clojure scripting runtime”.
* [Docker](https://docs.docker.com/get-docker/): The server and the search
  service require a Java runtime and [Apache Solr](https://solr.apache.org/). To
  ease the setup and deployment of these services, both are containerized and
  assume a Docker environment for testing and in production.

## Configuration

ZDL/Lex is configured via environment variables, in line with the [Twelve-Factor
App guidelines](https://12factor.net/). Variable settings are read from `.env`
files in the current working directory as well the respective process
environment.

To configure the build and development environment, copy `.env.sample` to `.env`
in the project directory and adjust the settings to your needs. See the comments
in the sample file for a documentation of the available options. Example:

```plaintext
# disconnect test setup from production origin
ZDL_LEX_GIT_ORIGIN=
ZDL_LEX_GIT_DIR=test-data/git
ZDL_LEX_LOCK_DB_PATH=test-data/locks

ZDL_LEX_MANTIS_DB_HOST=localhost
ZDL_LEX_MANTIS_DB_USER=testuser
ZDL_LEX_MANTIS_DB_PASSWORD=testpass

ZDL_LEX_METRICS_REPORT_INTERVAL=1440

ZDL_LEX_SERVER_URL=http://localhost:3000/
ZDL_LEX_SERVER_USER=admin
ZDL_LEX_SERVER_PASSWORD=admin
```

## Build

Build tasks can be executed via `bb`:

```plaintext
$ bb tasks
The following tasks are available:

clj-kondo     Configures clj-kondo linter
pyenv         Initializes local python environment
python-deps   Installs Python dependencies
write-version Writes new version
build-schema  Transpile RELAXNG/Schematron sources
build-client  Builds Oxygen XML Editor plugin/framework
build         Builds docker images
release       Releases docker images
test          Runs test suite
start-oxygen  Start Oxygen XML Editor with plugin/framework
```

Accordingly, to build the application's client and server components:

```plaintext
$ bb build
[…]
Successfully built […]
Successfully tagged docker-registry.zdl.org/zdl-lex/server:latest
```

## Test

Before releasing new versions of the application, its client and server
components can be tested locally.

Make sure settings in `.env` point to the local server instance and provide test
credentials, i.e.:

```plaintext
ZDL_LEX_SERVER_URL=http://localhost:3000/
ZDL_LEX_SERVER_USER=admin
ZDL_LEX_SERVER_PASSWORD=admin
```

To run a local server instance as a Docker container:

```plaintext
$ bb build
$ docker-compose up
```

The server component relies on [Apache Solr](https://lucene.apache.org/solr/)
for its facetted search functionality, reachable via 

http://localhost:8983/solr

The server container is reachable at

http://localhost:3000/

Then, start the Oxygen XML Editor with the client plugin installed from the
current project sources via

```plaintext
$ bb start-oxygen
```

## Release

Releasing a new version of ZDL/Lex entails a complete build of all components,
packaging everything in Docker containers and pushing the containers' images
with updated tags to ZDL's private Docker registry:

```plaintext
$ bb release
```

## License

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
