SHELL := /bin/bash

version := $(shell cat VERSION)

server-jar = server/target/uberjar/zdl-lex-server-$(version)-standalone.jar
client-pkg = client/target/oxygen/updateSite.xml

all: $(server-jar) $(client-jar)

ansible/venv:
	cd ansible &&\
		virtualenv venv && source venv/bin/activate &&\
		pip install -r requirements.txt

deploy: ansible/venv $(server-jar) $(client-pkg)
	cd ansible &&\
		source venv/bin/activate &&\
		ansible-playbook main.yml -b -K --ask-vault-pass

clean:
	cd client && lein clean
	cd server && lein clean

oxygen: $(client-pkg)
	cd client && lein oxygen

$(server-jar):
	cd server && lein clean && lein uberjar

$(client-pkg):
	cd client && lein clean && lein uberjar && lein package

.PHONY: all deploy clean oxygen
