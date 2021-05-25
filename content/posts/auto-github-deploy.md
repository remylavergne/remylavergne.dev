---
title: Auto Deploy Test
date: 2021-05-25T19:33:59+02:00
lastmod: 2021-05-25T19:33:59+02:00
author: Rémy Lavergne
avatar: /me/mugshot.jpg
# authorlink: https://author.site
cover: /post-headers/auto-github-deploy.jpg
categories:
  - Dev
tags:
  - docker
  - github
  - kotlin
  - automation
# nolastmod: true
draft: false
---

Article de test pour savoir si l'automatisation de la mise à jour de mon blog fonctionne.

<!--more-->

Ce blog est hébergé sur [GitHub](https://github.com/remylavergne/remylavergne.dev), et je voulais trouver une moyen simple de pouvoir automatiser sa mise à jour, sans devoir passer par mon SFTP pour téléverser moi-même le site. Ce process est long, et pas fun du tout.

Je suis parti dans l'idée de faire un petit script pour automatiser ce processus. Mais comment fonctionne-t'il ?

- Le script fait un fetch du repository toutes les 30 minutes (valeur arbitraire que j'ai imposé)
- Si une différence est détectée, un pull est effectué, et ensuite, le site est build via Hugo
- Suite au build, tout les fichiers sont copiés dans le dossier public pour exposer le blog à jour

Pour cela, j'ai créé une image [Docker](https://github.com/remylavergne/remylavergne.dev/blob/master/Dockerfile) (avec pleins de valeurs en dur, bien comme il le faut 🤡) :

```dockerfile
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
```

Ce *Dockerfile* utilise un [script](https://github.com/remylavergne/remylavergne.dev/blob/master/update.main.kts) écrit en *Kotlin* :

```kotlin
#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")

import kotlinx.coroutines.*
import java.io.File

val targetDirectory: File = File("public")
val tempDir: File = File("temp")

fun String.execute() {
    val command = this.split(" ")
    ProcessBuilder()
        .command(command)
        .inheritIO()
        .start()
        .waitFor()
}

suspend fun scheduleRepeatedly(delayTimeMillis: Long = 30 * 60_000, action: suspend CoroutineScope.() -> Unit) =
    coroutineScope {
        while (true) {
            launch { action() }
            delay(delayTimeMillis)
        }
    }

fun projectNotCloned(): Boolean {
    return !File("temp").exists()
}

fun cloneProject() {
    tempDir.mkdir()
    "git clone --recurse-submodules https://github.com/remylavergne/remylavergne.dev.git temp".execute()
}

fun fetchRepo(): Boolean {
    val output = File("output.txt").apply { createNewFile() }
    "git -C temp fetch".execute()

    val dataAvailable = output.readText().isNotEmpty()
    output.deleteOnExit()

    return dataAvailable
}

fun pullLatestVersion() {
    "git -C temp pull".execute()
    "git submodule update --recursive --remote".execute()
}

fun buildLatestVersion() {
    "hugo -D -s temp".execute()
}

fun deployLatestVersion() {
    "cp -R temp/public/. public/".execute()
}

runBlocking {

    targetDirectory.mkdir()

    scheduleRepeatedly {

        if (projectNotCloned()) {
            cloneProject()
            buildLatestVersion()
            deployLatestVersion()
        }

        val updateAvailable: Boolean = fetchRepo()

        if (updateAvailable) {
            pullLatestVersion()
            buildLatestVersion()
            deployLatestVersion()
        } else {
            println("No recent data found. Sleep...")
        }
    }
}
```

Le tout est assemblé dans un *docker-compose* :

```yaml
version: "3.8"

services:
  web:
    image: nginx:latest
    container_name: remylavergne-dev
    expose:
      - 80
    volumes:
      - ./public:/usr/share/nginx/html
    environment:
      VIRTUAL_HOST: remylavergne.dev
      LETSENCRYPT_HOST: remylavergne.dev
      LETSENCRYPT_EMAIL: lavergne.remy@gmail.com
    logging:
      driver: "json-file"
      options:
        max-file: "5"
        max-size: "10m"

  auto-update:
    build: .
    container_name: remylavergne-dev-auto-update
    volumes:
      - ./public:/usr/src/app/public
    logging:
      driver: "json-file"
      options:
        max-file: "5"
        max-size: "10m"

networks:
  default:
    external:
      name: nginx-proxy
```
