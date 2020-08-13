.PHONY: build
build:
	$(MAKE) -C build build_env
	docker run -it --rm\
		-v $$SSH_AUTH_SOCK:/ssh-agent\
		-e SSH_AUTH_SOCK=/ssh-agent\
		-v $$PWD:/src\
		-w /src\
		lex.dwds.de/zdl-lex/build:1.0\
		make docker_build

.PHONY: docker_build
docker_build:
	mkdir -m 0700 /root/.ssh || true
	ssh-keyscan -H git.zdl.org >>/root/.ssh/known_hosts
	nohup Xvfb :99 -nolisten tcp >/dev/null &
	DISPLAY=:99 $(MAKE) -C build
