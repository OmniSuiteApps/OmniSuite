# OmniSuite

OmniSuite installe et met à jour OmniLauncher, OmniKeyboard, OmniBackground et OmniPhone depuis leurs releases GitHub (`OmniSuiteApps/<appli>`), avec le
même style que OmniLauncher (Material You, fond d'écran visible derrière un voile, cartes translucides).

Pour chaque appli : version installée, dernière release, bouton *Installer* / *Mettre à jour* / *Ouvrir*.
Chaque APK est vérifié par l'empreinte SHA-256 publiée par GitHub, puis par Android (même signature que l'appli
installée). La première installation par OmniSuite demande une confirmation ; ensuite, OmniSuite étant l'installeur
de l'appli, ses mises à jour passent sans question.

## Ajouter une appli

Rien à changer dans OmniSuite : chaque dépôt de `OmniSuiteApps` est une appli. Il suffit que le dépôt ait :

1. `app/build.gradle.kts` avec son `applicationId`
2. une activité d'accueil (MAIN/LAUNCHER, ou MAIN/HOME pour un lanceur), pour qu'OmniSuite lise sa version installée
3. une release GitHub avec l'APK signé en pièce jointe

La fiche prend le nom et la description du dépôt, et l'icône dans `app/src/main/res/drawable/ic_launcher_foreground.xml`
(ou `ic_launcher.xml`). Sans icône lisible, l'initiale du nom la remplace.

## Compiler et installer

Même clé de signature qu'OmniLauncher (`keystore.properties`, hors git).

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleRelease
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

## Tests

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest
```
