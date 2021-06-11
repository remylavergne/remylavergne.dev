---
title: "Git difftool : Intellij, VS Code, ou Kdiff3 ?"
date: 2021-06-11T15:34:29+02:00
lastmod: 2021-06-11T15:34:29+02:00
author: Rémy Lavergne
avatar: /me/mugshot.jpg
# authorlink: https://author.site
cover: /post-headers/git-difftool.jpeg
categories:
  - Dev
tags:
  - git
# nolastmod: true
draft: false
---

En temps que développeur, travaillant au sein d'une équipe, nous devons souvent faire de la review de code. Jusqu'à aujourd'hui, j'ai toujours utilisé les outils natifs fournis par **GitLab**, **GitHub**, ou encore **BitBucket**.

Jusqu'au moment où je me suis rendu compte que l'on pouvait paramétrer, et utiliser, directement nos IDEs pour faire ces reviews.

<!--more-->

Les principaux logiciels que j'utilise pour développer sont **VS Code** et **IntelliJ**. J'aime beaucoup la légèreté de **VS Code**, mais ce que j'aime encore plus c'est la façon dont **IntelliJ** affiche les différences des fichiers que j'édite. Mais comment les utiliser ?

### macOS

Tout se passe au niveau du fichier **~/.gitconfig**. Il faut ajouter la configuration suivante, en fonction du logiciel que l'on veut utiliser.

Pour utiliser **IntelliJ** :

```
[diff]
    tool = intellij
[difftool "intellij"]
    cmd = /Applications/IntelliJ\\ IDEA.app/Contents/MacOS/idea diff $(cd $(dirname "$LOCAL") && pwd)/$(basename "$LOCAL") $(cd $(dirname "$REMOTE") && pwd)/$(basename "$REMOTE")
# Configuration pour le mergetool
[merge]
    tool = intellij
[mergetool "intellij"]
    cmd = /Applications/IntelliJ\\ IDEA.app/Contents/MacOS/idea merge $(cd $(dirname "$LOCAL") && pwd)/$(basename "$LOCAL") $(cd $(dirname "$REMOTE") && pwd)/$(basename "$REMOTE") $(cd $(dirname "$BASE") && pwd)/$(basename "$BASE") $(cd $(dirname "$MERGED") && pwd)/$(basename "$MERGED")
    trustExitCode = true
```

Pour utiliser **VS Code** :

```
[diff]
    tool = vscode
[difftool "vscode"]
    cmd = code --wait --diff $LOCAL $REMOTE
# Configuration pour le mergetool
[merge]
    tool = vscode
[mergetool "vscode"]
    cmd = code --wait $MERGED
```

Pour utiliser **Kdiff3** :

```
[difftool "kdiff3"]
    path = /Applications/kdiff3.app/Contents/MacOS/kdiff3
[diff]
    tool = kdiff3
```

_Cover: [@yancymin](https://unsplash.com/@yancymin)_

Sources:

- <https://stackoverflow.com/questions/33308482/git-how-configure-kdiff3-as-merge-tool-and-diff-tool>
- <https://stackoverflow.com/questions/33722301/how-to-setup-kdiff3-in-mac-os>
- <https://coderwall.com/p/gc_hqw/use-intellij-or-webstorm-as-your-git-diff-tool-even-on-windows>
- <https://www.kimsereylam.com/git/vscode/2020/12/25/git-difftool-and-mergetool-with-visual-studio-code.html>
