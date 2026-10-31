# iosApp

Projet Xcode hôte pour la cible iOS. Sur un Mac :

1. Ouvrir `iosApp/` avec Xcode (créer le projet via **File > New > Project > iOS App**, nom `iosApp`, bundle `fr.abls.habitat.home`, et y ajouter `iOSApp.swift` + `Info.plist`),
2. Ajouter une *Run Script* phase : `cd "$SRCROOT/.." && ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`,
3. Lier le framework `ComposeApp` (`composeApp/build/xcode-frameworks`).

Le build iOS n'est pas réalisé par la CI (nécessite macOS).
