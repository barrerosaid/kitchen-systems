# syntax=docker/dockerfile:1
FROM gradle:jdk21-alpine@sha256:17ded0aba46345da4d63308c06e181fcb8f831ed3a6342445d1f8adf6578331c
WORKDIR /app

COPY settings.gradle build.gradle ./
RUN /usr/bin/gradle --no-daemon installDist

COPY src ./src
RUN /usr/bin/gradle --no-daemon installDist

ENTRYPOINT ["/app/build/install/challenge/bin/challenge"]
