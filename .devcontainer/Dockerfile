# Folkhold development image: JDK 21, Git, packwiz and Python 3 (for the scripts in tools/).
# The Gradle wrapper downloads Gradle into the Gradle cache volume (/home/dev/.gradle).

FROM golang:1.27-bookworm AS packwiz
# packwiz has no releases. The commit hash gives a reproducible build. The CI uses the same commit.
ARG PACKWIZ_VERSION=ef87d964f8cbd52b3b13ea42453ef322290e2b9e
RUN CGO_ENABLED=0 go install github.com/packwiz/packwiz@${PACKWIZ_VERSION}

FROM eclipse-temurin:21-jdk-noble

RUN apt-get update \
    && apt-get install -y --no-install-recommends git unzip curl ca-certificates python3 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=packwiz /go/bin/packwiz /usr/local/bin/packwiz

# Non-root user with UID 1000. The base image has a user 'ubuntu' with this UID: remove it.
RUN (userdel --remove ubuntu 2>/dev/null || true) \
    && useradd --create-home --uid 1000 --shell /bin/bash dev \
    && mkdir -p /home/dev/.gradle \
    && chown dev:dev /home/dev/.gradle \
    && git config --system --add safe.directory '*'

USER dev
ENV GRADLE_USER_HOME=/home/dev/.gradle
WORKDIR /workspace
