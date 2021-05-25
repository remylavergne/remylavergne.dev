FROM openjdk:17-jdk-alpine3.13

LABEL maintainer="contact@remylavergne.dev"

WORKDIR /usr/src/app

ENV HUGO_VERSION="0.83.1"

RUN mkdir public

COPY update.main.kts .

RUN apk update && apk add bash curl git zip alpine-sdk libc6-compat && curl -s "https://get.sdkman.io" | bash

RUN /bin/bash -c "source /root/.sdkman/bin/sdkman-init.sh; sdk version; sdk install kotlin"

RUN curl -fsSLO --compressed "https://github.com/gohugoio/hugo/releases/download/v${HUGO_VERSION}/hugo_extended_${HUGO_VERSION}_Linux-64bit.tar.gz" \
  && tar -xzf hugo_extended_${HUGO_VERSION}_Linux-64bit.tar.gz -C /usr/local/bin/ \
  && rm hugo_extended_${HUGO_VERSION}_Linux-64bit.tar.gz

ENV PATH=/root/.sdkman/candidates/kotlin/current/bin:$PATH

ENTRYPOINT ["kotlin", "update.main.kts"]