# ZDL/Lex – Lexikographic Workbench

_A client/server application implementing an authoring environment for
lexicographic articles at the [ZDL](https://www.zdl.org/)_

![Schreibtisch eines Philologen by Die.keimzelle / Wikimedia Commons / CC-BY-3.0](https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Schreibtisch_eines_Philologen.jpg/640px-Schreibtisch_eines_Philologen.jpg)

## Prerequisites

* [GNU/Linux](https://www.debian.org/) (tested on Debian-based distros)
* [Docker](https://www.docker.com/)
* [Java 8](https://packages.debian.org/search?keywords=openjdk-8-jdk)

The application client is implemented as a plugin for [Oxygen XML
Editor](https://www.oxygenxml.com/). We strive for compatibility with the
editor's v20 which incorporates JDK v8. Thus, the client plugin has to be
compiled with the apropriate JDK v8 API bindings. In the likely case that your
default JDK is of a later version, you can install v8 in parallel and point the
build scripts to its location. See below for configuration details.

While the client and server component compile to Java bytecode and run natively
on the Java Virtual Machine, for building the application a Clojure installation
is required. [Clojure](https://clojure.org/) is installed locally in the project
as part of the build.

### Optional Requirements for Tooling/Scripts

* [Python 3](https://www.python.org/)

For Python-based tooling and scripts in `publish/`, it is recommended to use a
project-specific virtual environment, e.g. via
[pyenv](https://github.com/pyenv/pyenv):

```plaintext
$ pyenv virtualenv zdl-lex
$ pyenv local zdl-lex
```

Then, install Python dependencies and local modules via

```plaintext
$ pip install -r requirements.txt
```

## Configuration

ZDL/Lex is configured via environment variables, in line with the [Twelve-Factor
App guidelines](https://12factor.net/). Variable settings are read from `.env`
files in the current working directory as well the respective process
environment.

To configure the build and development environment, copy `.env.sample` to `.env`
in the project directory and adjust the settings to your needs. See the comments
in the sample file for a documentation of the available options. Example:

```plaintext
ZDL_LEX_JAVA8_HOME=/usr/lib/jvm/java-8-openjdk-amd64

ZDL_LEX_DATA_DIR=../data

ZDL_LEX_METRICS_REPORT_INTERVAL=0

# disconnect test setup from production origin
ZDL_LEX_GIT_ORIGIN=

ZDL_LEX_MANTIS_USER=lexuser
ZDL_LEX_MANTIS_PASSWORD=secret123!
```

## Build

Build tasks can be executed via `make`:

```plaintext
$ make help
Targets:

 all      - Builds client, server and packages both in a Docker
            container
 release  - Runs a test build, creates a release tag for the current
            git revision and reruns the build, pushing resulting
            images in the end
 client   - Builds client and starts an OxygenXML Editor instance
            with the built development version of the client
```

Accordingly, to build the application's client and server components:

```plaintext
$ make all
[main            | INFO  | zdl.lex.build       ] Transpiling Artikel-XML schema (RNC -> RNG)
[main            | INFO  | zdl.lex.build       ] Compiling Oxygen XML Editor plugin (client)
[main            | INFO  | zdl.lex.build       ] Packaging Oxygen XML Editor plugin (client)
[main            | INFO  | zdl.lex.build       ] Compiling server
[main            | INFO  | zdl.lex.build       ] Packaging server
[…]
Successfully built a57682690b6e
Successfully tagged docker-registry.zdl.org/zdl-lex/server:35695b8
make[1]: Leaving directory '/home/gregor/repositories/zdl-lex/docker/server'
```

## Test

Before releasing new versions of the application, its client and server
components can be tested locally.

To run a local server instance as a Docker container:

```plaintext
$ make all
$ docker-compose up
```

The server component relies on [Apache Solr](https://lucene.apache.org/solr/)
for its facetted search functionality, reachable via 

http://localhost:8983/solr

The server container is reachable at

http://localhost:3000/

Before starting Oxygen XML Editor with the client plugin installed from the
current project sources, make sure settings in `.env` point to the local server
instance and provide test credentials:

```plaintext
ZDL_LEX_SERVER_URL=http://localhost:3000/
ZDL_LEX_SERVER_USER=admin
ZDL_LEX_SERVER_PASSWORD=admin
```

Then, start the client via

```plaintext
$ make client
```

## Release

Releasing a new version of ZDL/Lex entails a complete build of all components,
packaging everything as a Docker container and pushing the container images with
updated tags to the ZDL's private Docker registry:

```plaintext
$ make release
```

## License

Copyright © 2019-2021 Berlin-Brandenburgische Akademie der Wissenschaften.

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
