FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-21-jdk \
    maven \
    curl \
    unzip \
    git \
    bash \
    && rm -rf /var/lib/apt/lists/*

# Install CodeQL
RUN curl -L -o codeql.zip https://github.com/github/codeql-cli-binaries/releases/download/v2.21.4/codeql-linux64.zip \
    && unzip codeql.zip -d /opt \
    && mv /opt/codeql /opt/codeql-cli \
    && ln -s /opt/codeql-cli/codeql /usr/local/bin/codeql

# Set environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV CODEQL_HOME=/opt/codeql-cli
ENV PATH="$JAVA_HOME/bin:$PATH:$CODEQL_HOME"

WORKDIR /app
