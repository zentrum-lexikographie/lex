SHELL := /bin/bash

version := $(shell cat VERSION)

server-jar = server/target/uberjar/zdl-lex-server-$(version)-standalone.jar
client-jar = client/project.clj

all: $(server-jar) $(client-jar)

ansible/venv:
	cd ansible &&\
		virtualenv venv && source venv/bin/activate &&\
		pip install -r requirements.txt

deploy: ansible/venv $(server-jar) $(client-jar)
	cd ansible &&\
		source venv/bin/activate &&\
		ansible-playbook main.yml -b -K --ask-vault-pass

clean:
	cd client && lein clean
	cd server && lein clean

$(server-jar):
	cd server && lein clean && lein uberjar

$(client-jar):
	cd client && lein clean && lein uberjar && lein package

.PHONY: all deploy clean
