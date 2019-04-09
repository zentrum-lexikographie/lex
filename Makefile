SHELL := /bin/bash

version := $(shell cat VERSION)

all: server client

ansible/venv:
	cd ansible &&\
		virtualenv venv && source venv/bin/activate &&\
		pip install -r requirements.txt

vm: ansible/venv server client
	cd ansible &&\
		source venv/bin/activate &&\
		vagrant up

vm-destroy:
	cd ansible &&\
		vagrant destroy

deploy: ansible/venv server client
	cd ansible &&\
		source venv/bin/activate &&\
		ansible-playbook main.yml -b -K --ask-vault-pass

clean:
	cd client && lein clean
	cd server && lein clean

oxygen: client
	cd client && lein oxygen

server:
	cd server && lein uberjar

client:
	cd client && lein uberjar && lein package

.PHONY: all deploy clean vm vm-destroy oxygen server client
