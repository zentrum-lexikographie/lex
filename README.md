# ZDL/Lex – Lexikographic Workbench

## Build Requirements

* [Java 8](https://packages.debian.org/search?keywords=openjdk-8-jdk)
* [Clojure](https://clojure.org/)
* [Leiningen](https://leiningen.org/)
* [Python 3](https://www.python.org/)
* [Ansible](https://www.ansible.com/)
* [Poetry](https://poetry.eustace.io/)

Java 8 is needed, as Oxygen XML does not work with newer versions.

## Setup

    $ pip install ansible
    $ poetry install

## Build and Deploy

    $ make deploy
    
## License

Copyright © 2019 Zentrum für digitale Lexikographie der deutschen Sprache.

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
