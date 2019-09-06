SHELL := /bin/bash

version := $(shell cat VERSION)
oxygen_home := $(shell bin/find-oxygen.sh)

.PHONY: all
all: server client

bin/lein:
	curl -s -o $@\
		https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
	chmod +x $@

.PHONY: common
common: bin/lein
	cd common && ../bin/lein install

.PHONY: server
server: bin/lein common schema
	cd server && ../bin/lein uberjar

.PHONY: client
client: bin/lein client/target/zdl-lex-client.jar schema
	cd build && ../bin/lein run -m zdl-lex-build.client

client/target/zdl-lex-client.jar: bin/lein common
	cd client && ../bin/lein uberjar

.PHONY: schema
schema:
	make -C $@ all

.PHONY: clean
clean: bin/lein
	$(RM) -r chrome-driver
	make -C schema clean
	cd common && ../bin/lein clean
	cd client && ../bin/lein clean
	cd server && ../bin/lein clean

.PHONY: oxygen
oxygen: client
	$(RM) client/.lein-env
	cd client && OXYGEN_HOME="$(oxygen_home)"\
		"$(oxygen_home)/jre/bin/java"\
			-Dzdl.lex.repl.port=3001\
			-Dcom.oxygenxml.editor.plugins.dir=target/oxygen/plugins\
			-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor\
			-cp "$(oxygen_home)/lib/oxygen.jar:$(oxygen_home)/lib/oxygen-basic-utilities.jar:$(oxygen_home)/classes:$(oxygen_home)"\
			ro.sync.exml.Oxygen\
			test-project.xpr

.PHONY: deploy
deploy:
	make -C ansible deploy
.PHONY: spock-tunnel
spock-tunnel:
	ssh -N -L 8080:localhost:8080 -o "ServerAliveInterval 60" -v spock.dwds.de

.PHONY: new-version
new-version:
	echo -n `date +%Y%m.%d.%H` >VERSION
