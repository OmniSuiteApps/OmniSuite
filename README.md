# OmniSuite

OmniSuite installe et met à jour OmniLauncher, OmniKeyboard et OmniBackground depuis leurs releases GitHub (`OmniSuiteApps/<appli>`), avec le
même style que OmniLauncher (Material You, fond d'écran visible derrière un voile, cartes translucides).

Pour chaque appli : version installée, dernière release, bouton *Installer* / *Mettre à jour* / *Ouvrir*.
Chaque APK est vérifié par l'empreinte SHA-256 publiée par GitHub, puis par Android (même signature que l'appli
installée). La première installation par OmniSuite demande une confirmation ; ensuite, OmniSuite étant l'installeur
de l'appli, ses mises à jour passent sans question.

## Ajouter une appli

OmniSuite est elle-même dans la liste : la mettre à jour (release sur `OmniSuiteApps/OmniSuite`) apporte les nouvelles applis.

1. Une ligne dans `CATALOG` (`Catalog.kt`)
2. Son paquet dans `<queries>` (`AndroidManifest.xml`)
3. Une release GitHub avec l'APK signé en pièce jointe

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
