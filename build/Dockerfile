# JDK v8 is needed for Oxygen XML Editor v20 compatibility
FROM clojure:openjdk-8-tools-deps

# The Oxygen Plugin has Swing/AWT bindings which have to be available during
# compilation
RUN apt-get update &&\
  apt-get install -y xvfb libxrender1 libfontconfig1 libxtst6 &&\
  rm -rf /var/lib/apt/lists/*
