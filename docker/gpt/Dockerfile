FROM python:3-slim-bullseye

RUN apt-get -qq update\
    && apt-get install -qq -y\
        libopenblas-dev ninja-build build-essential pkg-config\
    && apt-get -qq -y autoremove \
    && apt-get autoclean \
    && rm -rf /var/lib/apt/lists/* /var/log/dpkg.log


RUN python -m pip install --upgrade pip llama_cpp_python[server]

# We need to set the host to 0.0.0.0 to allow outside access
ENV HOST 0.0.0.0

ENTRYPOINT ["python3", "-m", "llama_cpp.server"]
