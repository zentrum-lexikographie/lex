NAME := docker-registry.zdl.org/zdl-lex/server
TAG := $$(git log -1 --pretty=%h)
VERSION := $$(git describe --tags --abbrev=0 --always)

IMG := ${NAME}:${TAG}
RELEASE := ${NAME}:${VERSION}
LATEST := ${NAME}:latest

.PHONY: build
build:
	@docker build -t ${IMG} .
	@docker tag ${IMG} ${RELEASE}
	@docker tag ${IMG} ${LATEST}

.PHONY: push
push:
	@docker push ${RELEASE}
