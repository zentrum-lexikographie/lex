# ZDL/Lex – Lexikographic Workbench

_A client/server application serving as the authoring environment for
lexicographic articles at the [ZDL](https://www.zdl.org/)_

![Schreibtisch eines Philologen by Die.keimzelle / Wikimedia Commons / CC-BY-3.0](https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Schreibtisch_eines_Philologen.jpg/640px-Schreibtisch_eines_Philologen.jpg)

## Prerequisites

* [Java 8](https://packages.debian.org/search?keywords=openjdk-8-jdk)
* [Docker](https://www.docker.com/)

The application client is implemented as a plugin for [Oxygen XML
Editor](https://www.oxygenxml.com/). We strive for compatibility with the
editor's v20 which incorporates JDK v8. Thus, the client plugin has to be
compiled with the apropriate JDK v8 API bindings. In the likely case that your
default JDK is of a later version, you can install v8 in parallel and point the
build scripts to its location. See below for configuration details.

### Build Requirements

* [Clojure 1.10](https://clojure.org/)

While the client and server component compile to Java bytecode and run natively
on the Java Virtual Machine, for building the application a Clojure installation
is required.

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

 build    - Builds client, server and packages both in a Docker
            container
 release  - Runs a test build, creates a release tag for the current
            git revision and reruns the build, pushing resulting
            images in the end
 client   - Builds client and starts an OxygenXML Editor instance
            with the built development version of the client
 server   - Starts local ZDL-Lex server as a Docker container
 solr     - Starts local Apache Solr server as a Docker container
```

Accordingly, to build the application's client and server components:

```plaintext
$ make build
make[1]: Entering directory '/home/gregor/repositories/zdl-lex/build'
[2021-02-16 13:10:58,116 | zdl.lex.build        ] Transpiling Artikel-XML schema (RNC -> RNG)
[2021-02-16 13:10:58,402 | zdl.lex.build        ] Compiling Oxygen XML Editor plugin (client)
[2021-02-16 13:11:24,778 | zdl.lex.build        ] Packaging Oxygen XML Editor plugin (client)
[2021-02-16 13:11:28,268 | zdl.lex.build        ] Compiling CLI
[2021-02-16 13:11:32,426 | zdl.lex.build        ] Packaging CLI
[2021-02-16 13:11:34,730 | zdl.lex.build        ] Compiling server
[2021-02-16 13:12:01,395 | zdl.lex.build        ] Packaging server
make[1]: Leaving directory '/home/gregor/repositories/zdl-lex/build'
make[1]: Entering directory '/home/gregor/repositories/zdl-lex/docker/solr'
Sending build context to Docker daemon  213.5kB
[…]
Successfully built 66475cb0e5c1
Successfully tagged lex.dwds.de/zdl-lex/solr:be91da8
make[1]: Leaving directory '/home/gregor/repositories/zdl-lex/docker/solr'
make[1]: Entering directory '/home/gregor/repositories/zdl-lex/docker/server'
Sending build context to Docker daemon  132.5MB
[…]
Successfully built 9cd3dbf51b66
Successfully tagged lex.dwds.de/zdl-lex/server:be91da8
make[1]: Leaving directory '/home/gregor/repositories/zdl-lex/docker/server'
```

## Test

Before releasing new versions of the application, its client and server
components can be tested locally.

To run a local server instance as a Docker container:

```plaintext
$ make solr
$ make server
```

The server component relies on [Apache Solr](https://lucene.apache.org/solr/)
for its facetted search functionality. The first command spawns a Solr Docker
container, reachable via 

http://localhost:8983/solr

The second command spawns a server container, reachable at

http://localhost:3000/

You can check the server logs via

```plaintext
$ docker logs zdl_lex_server 
[…]
[main       | INFO  | zdl.lex.server.git  ] git version 2.20.1
[main       | INFO  | zdl.lex.server.git  ] {:git {:init /data/git}}
[main       | INFO  | zdl.lex.server.git  ] {:git {:repo /data/git, :branch zdl-lex-server/development, :origin nil}}
[dispatch-1 | INFO  | zdl.lex.cron        ] {:desc Solr index rebuild, :cron 0 0 1 * * ?, :req :init}
[main       | INFO  | zdl.lex.server      ] Started ZDL/Lex Server @[/data]
```

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
