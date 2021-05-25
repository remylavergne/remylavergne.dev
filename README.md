My [blog](https://remylavergne.dev/) based on [Go Hugo Framework](https://gohugo.io/)

Custom made to auto-build / auto-deploy it with Docker from GitHub.

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

Explication de comment j'ai automatisé la mise à jour de mon blog avec **GitHub**, **Docker** et **Kotlin**.

<!--more-->

## Contexte et cas d'usage

Ce blog est hébergé sur [GitHub](https://github.com/remylavergne/remylavergne.dev), et je voulais trouver une moyen simple de pouvoir automatiser sa mise à jour, sans devoir passer par mon ftp pour upload moi-même les fichiers générés par _Hugo_. Ce process est long, et pas fun du tout.

## Processus

Mon blog est versionné sur _GitHub_, je suis donc parti sur la solution suivante :

- Faire des **fetchs** périodique sur le repository (~ 30 minutes)
- Lorsque des changements sont détectés, faire un **pull** du repository pour mettre à jour la branche locale
- Lancer un build pour générer la nouvelle version du site
- Copier le site généré dans le dossier _public_ (== déploiement)

Ces opérations sont faites via un script écrit en _Kotlin_. Ce script est ensuite [dockérizé](https://github.com/remylavergne/remylavergne.dev/blob/master/Dockerfile) dans une image qui contient tous les outils pour faire ces opérations (git, Hugo, Kotlin, ...).

### Dockerfile

```text
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

### Le script Kotlin

```kotlin
#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")

import kotlinx.coroutines.*
import java.io.File

val targetDirectory: File = File("public")
val tempDir: File = File("temp")

fun String.execute(): String {
    val command = this.split(" ")
    val output = File("output.txt")
    output.createNewFile()

    ProcessBuilder()
        .command(command)
        .redirectErrorStream(true)
        .redirectOutput(output)
        .start()
        .waitFor()

    val o = output.readText()
    println(o)
    output.deleteOnExit()

    return o
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
    val output: String = "git -C temp fetch".execute()

    val dataAvailable = output.isNotEmpty()

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

### Le Docker Compose

Deux services se partagent le même dossier `public`. C'est ce dossier qui expose publiquement le blog via l'adresse <https://remylavergne.dev>.
Le service `web` expose le blog, tandis que le service `auto-update` s'occupe de build la dernière version disponible du blog.

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

## Problématiques rencontrées

Pendant la création du _Dockerfile_ j'ai rencontré quelques difficultés causées par l'utilisation de la version _Alpine_ de l'[OpenJDK](https://hub.docker.com/_/openjdk/). Cette version allégée manque de pas mal de dépendances, et moi de connaissances...

Après l'installation de **Go Hugo**, je faisais un check de sa version pour vérifier que tout était en ordre avec un simple `hugo version`, mais cette erreur s'affichait à chaque fois :

```shell
error while loading shared libraries: libstdc++.so.6: cannot open shared object file: No such file or directory
```

Après quelques longues recherches, il s'est avéré qu'il me manquait ces deux dépendances : `alpine-sdk libc6-compat`.

## Pistes d'améliorations

- L'image _Alpine_ pèse ~ 180Mo maximum, mais après la génération de l'image, celle-ci pèse aux alentours de 750Mo ! Je vais essayer de comprendre ce que peux autant gonfler sa taille. Et voir si je ne peux pas nettoyer certaines dépendances.
- En l'état actuel, le mécanisme est très basique car n'importe quelle différence sur le repository aura pour effet de lancer une nouveau build + déploiement du blog (par exemple: si le *README.md* est édité, le site sera redéployé).
  Ceci n'est pas forcément bloquant / contraignant.

