NAME := lex.dwds.ds/zdl-lex/server
TAG := $$(git log -1 --pretty=%h)
IMG := ${NAME}:${TAG}
LATEST := ${NAME}:latest

.PHONY: build
build:
	@docker build -t ${IMG} .
	@docker tag ${IMG} ${LATEST}

.PHONY: push
push:
	@docker push ${NAME}
