# ZDL/Lex – Lexikographic Workbench

_A client/server application implementing an authoring environment for
lexicographic articles at the [ZDL](https://www.zdl.org/)_

![Schreibtisch eines Philologen by Die.keimzelle / Wikimedia Commons / CC-BY-3.0](https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Schreibtisch_eines_Philologen.jpg/640px-Schreibtisch_eines_Philologen.jpg)

## Prerequisites

For building versions of the authoring environment, the following
software is required:

* [GNU/Linux](https://www.debian.org/): Development, builds and tests
  of the platform are performed on Linux. While other UNIX-like
  operating systems (i. e. MacOS) might work, they are not
  supported. The same goes for MS Windows.
* [Docker](https://docs.docker.com/get-docker/): The server and the search
  service require a Java runtime and [Apache Solr](https://solr.apache.org/). To
  ease the setup and deployment of these services, both are containerized and
  assume a Docker environment for testing and in production.

For developing the authoring environment, the following software is
required:

* [Clojure](https://clojure.org/): The server component, mediating between the
  [Oxygen-XML-Editor](https://www.oxygenxml.com/)-based client, a Git-based data
  store and a search service, is written Clojure, a LISP dialect. The same goes
  for Oxygen-XML's project-specific extensions.
* [Java (JDK)](https://openjdk.java.net/): Clojure, being a hosted language,
  requires a current Java runtime.

## Build and Release

Building a (new) version of the authoring environment:

```plaintext
$ scripts/release
```

This will trigger a build of 2 Docker container images, one for the
customized Solr Search Server, one for the HTTP-based middleware, which
also contains the plugin and a framework extending Oxygen-XML-Editor.

After the build finishes, the images are pushed to the ZDL-internal
Docker Registry for subsequent deployment.

## Development and Testing

### Configuration

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

ZDL_LEX_MANTIS_DB_HOST=localhost
ZDL_LEX_MANTIS_DB_USER=testuser
ZDL_LEX_MANTIS_DB_PASSWORD=testpass

ZDL_LEX_METRICS_REPORT_INTERVAL=1440

ZDL_LEX_SERVER_URL=http://localhost:3000/
ZDL_LEX_SERVER_USER=admin
ZDL_LEX_SERVER_PASSWORD=admin
```

### Test

Before releasing new versions of the application, its client and server
components can be tested locally.

Make sure settings in `.env` point to the local server instance and provide test
credentials, i.e.:

```plaintext
ZDL_LEX_SERVER_URL=http://localhost:3000/
ZDL_LEX_SERVER_USER=admin
ZDL_LEX_SERVER_PASSWORD=admin
```

To build and run a local server instance as a Docker container:

```plaintext
$ docker compose up --build
```

The server component relies on [Apache Solr](https://lucene.apache.org/solr/)
for its facetted search functionality, reachable via 

http://localhost:8983/solr

The server container is reachable at

http://localhost:3000/

Then, start the Oxygen XML Editor with the client plugin installed from the
current project sources via

```plaintext
$ clojure -T:build start-editor
```

### Extracting random test data from DWDS sources

    clojure -M:test:log -m zdl.lex.test-data $DWDS_WB_GIT_DIR

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
