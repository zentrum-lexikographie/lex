.PHONY: build_env
build_env:
	$(MAKE) -C build build_env

define with-build-env =
docker run -it --rm\
	-v $$SSH_AUTH_SOCK:/ssh-agent\
	-e SSH_AUTH_SOCK=/ssh-agent\
	-v $$PWD:/src\
	-w /src\
	lex.dwds.de/zdl-lex/build:1.0
endef

.PHONY: build
build: build_env
	$(with-build-env) make docker_build

.PHONY: release
release: build_env
	$(with-build-env) make docker_release

define with-docker-env
	mkdir -m 0700 /root/.ssh || true
	ssh-keyscan -H git.zdl.org >>/root/.ssh/known_hosts
	nohup Xvfb :99 -nolisten tcp >/dev/null &
	DISPLAY=:99
endef

.PHONY: docker_build
docker_build:
	$(with-docker-env) $(MAKE) -C build

.PHONY: docker_release
docker_release:
	$(with-docker-env) $(MAKE) -C build release
