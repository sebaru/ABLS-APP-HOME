# Abls-Habitat Home — application mobile

Réécriture mobile (Android + iOS) du frontend web [abls-habitat-home](https://github.com/sebaru/abls-habitat-home)
en **Kotlin Multiplatform** avec **Compose Multiplatform** : un seul code pour la logique métier *et* l'interface.

[![Android](https://github.com/sebaru/abls-app-home/actions/workflows/android.yml/badge.svg)](https://github.com/sebaru/abls-app-home/actions/workflows/android.yml)

## Fonctionnalités (parité avec la version web)

| Web (`abls-habitat-home`) | Mobile (`abls-app-home`) |
|---|---|
| Authentification Keycloak (`keycloak-js`) | OIDC Authorization Code + **PKCE** via navigateur système (Custom Tabs / `ASWebAuthenticationSession`), rafraîchissement automatique du jeton |
| Profil utilisateur, image et notifications de domaine | Idem (`/user/profil`, `/domain/image`) |
| Choix du domaine | Écran *Domaines* (`/domain/list`, `/user/set_domain`) |
| Synoptique : passerelles, vignettes d'anomalies, visuels (cadrans, jauges, clignotements), clic / clic long, saisie de consigne | Écran *Accueil* avec fil d'Ariane, grille de visuels responsive, `CadranDialog` |
| Mode d'affichage « lourd » (SVG) | Rendu en grille légère (v1) |
| Horloges et ticks | Écran *Horloge* (liste / ajout / suppression) |
| Caméras MJPEG et HLS | Décodage MJPEG en commun, HLS/MP4 par lecteur natif (Media3 / AVPlayer) |
| Tableaux de courbes (`/archive/get`, périodes) | Graphiques dessinés en Canvas + tableau de valeurs, rafraîchissement 60 s |
| Messages temps réel et acquittement | Écran *Messages* (filtre par typologie, MQTT `DLS_HISTO`) |
| Historique (`/histo/search`) | Écran *Historique* avec recherche et période |
| Temps réel MQTT over WebSocket (`mqtt.js`) | Client MQTT 3.1.1 minimal sur Ktor WebSocket (reconnexion, ping) |
| Envoi de la position GPS (mobile) | `GpsWatcher` natif (LocationManager / CoreLocation) |
| Console (niveau d'accès ≥ 6), compte Keycloak | Liens ouverts dans le navigateur |
| `config.js` | Écran *Paramètres* (URL API, IDP, realm, client) persisté sur l'appareil |

## Plan de réécriture

1. **Fondations** *(fait)* — projet KMP/Compose, catalogue de versions, CI GitHub Actions, licence GPL-2.0.
2. **Couche réseau & sécurité** *(fait)* — `ApiClient` (Bearer + `X-ABLS-DOMAIN`), modèles JSON tolérants, `AuthManager` PKCE, codec et gestionnaire MQTT.
3. **Écrans** *(fait)* — Config, Login, Domaines, Accueil (synoptique), Messages, Historique, Horloge, navigation et barre d'application.
4. **Natif Android** *(fait)* — Custom Tabs, deep link `abls.habitat.home://callback`, ExoPlayer HLS, GPS, icône adaptative.
5. **Natif iOS** *(code écrit, non compilé ici)* — `ASWebAuthenticationSession`, AVPlayer, CoreLocation ; projet Xcode à finaliser sur Mac (`iosApp/README.md`).
6. **Stabilisation** — tests sur appareil réel, retours terrain, correction des différences d'API, tests unitaires supplémentaires.
7. **v2** — mode lourd (rendu SVG des synoptiques), notifications push, widgets, publication Play Store / TestFlight.

## Architecture

```
composeApp/src
├── commonMain/kotlin/fr/abls/habitat/home
│   ├── App.kt                 # racine : phases Config → Login → Profil → navigation
│   ├── AppContainer.kt        # graphe d'objets (HttpClient, Session, Auth, Api, Mqtt, toasts)
│   ├── api/                   # ApiClient + modèles JSON
│   ├── auth/                  # OIDC PKCE (AuthManager, Pkce, AuthRedirectBus)
│   ├── config/                # AppConfig (URLs) persistée
│   ├── mqtt/                  # MqttCodec (3.1.1) + MqttManager (WebSocket)
│   ├── platform/Platform.kt   # expect : settings, navigateur, vidéo, GPS...
│   ├── session/               # Session (domaine, profil, access_level)
│   └── ui/                    # thème Bootstrap dark, composants, écrans
├── androidMain                # actuals Android, MainActivity, manifest, ressources
├── iosMain                    # actuals iOS, MainViewController
└── commonTest                 # tests PKCE / SHA-256 / MQTT
```

## Pré-requis côté serveur : client Keycloak

Créer dans le realm un client **public** (par ex. `abls-app-home`) :

- *Client authentication* : **Off** — *Standard flow* : **On** — PKCE : `S256` (*Advanced → Proof Key for Code Exchange Code Challenge Method*)
- *Valid redirect URIs* : `abls.habitat.home://callback`
- *Valid post logout redirect URIs* : `abls.habitat.home://callback`
- *Web origins* : `+`

Les mêmes rôles / mappers que le client web doivent être présents (l'API attend les mêmes jetons).

## Installation sur un mobile Android (mode opératoire)

1. **Télécharger l'APK** : page [Releases](https://github.com/sebaru/abls-app-home/releases) → fichier `abls-app-home-x.y.z.apk`
   (ou, pour une version de développement, l'artefact `abls-app-home-debug` d'un run de l'onglet *Actions*, à dézipper).
2. **Autoriser l'installation** : à l'ouverture du fichier, Android propose *Paramètres → Autoriser cette source* pour le navigateur ou le gestionnaire de fichiers utilisé. Accepter puis revenir.
3. **Installer** : appuyer sur *Installer*. Si Play Protect affiche un avertissement (application non publiée sur le Store), choisir *Installer quand même*.
4. **Premier lancement → écran Paramètres** : renseigner
   - URL de l'API (ex. `https://api.abls-habitat.fr`),
   - URL de l'IDP Keycloak (ex. `https://idp.abls-habitat.fr`),
   - Realm, Client ID (`abls-app-home`),
   puis *Enregistrer*.
5. **Se connecter** : le navigateur s'ouvre sur Keycloak ; après authentification, l'application reprend la main automatiquement.
6. **Autoriser la localisation** si demandé (facultatif : sert à `/user/set_gps`).
7. Si aucun domaine par défaut n'est défini, choisir un domaine dans l'écran *Domaines*.

Mises à jour : installer simplement le nouvel APK par-dessus (même signature requise ; voir ci-dessous).

### Installation via ADB (alternative)

```bash
adb install -r abls-app-home-x.y.z.apk
```

## Compilation locale

Pré-requis : JDK 17, Android SDK (API 35).

```bash
./gradlew :composeApp:assembleDebug          # APK debug
./gradlew :composeApp:testDebugUnitTest      # tests communs
```

## Livraison (CI)

- Chaque push sur `main` compile un APK debug (artefact *Actions*).
- Un tag `vX.Y.Z` produit une **GitHub Release** avec l'APK `abls-app-home-X.Y.Z.apk`.
- Pour signer avec votre propre clé (recommandé pour les mises à jour), définir les secrets du dépôt :
  `ABLS_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `ABLS_KEYSTORE_PASSWORD`, `ABLS_KEY_ALIAS`, `ABLS_KEY_PASSWORD`.
  Sans ces secrets, l'APK est signé avec la clé debug du runner (fonctionnel, mais la clé change à chaque exécution : désinstaller avant de réinstaller).

## Licence

GPL-2.0 — voir [LICENSE](LICENSE).
