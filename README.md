# DayDrop

Jeden Morgen ein neuer Drop: kuriose Tage, Ereignisse von heute vor X Jahren,
Fakten, die hängen bleiben, und eine Frage, bei der man erst raten muss. In ein
bis zwei Minuten durchgeswipet.

Android-App, privat gebaut. Fertige APK: **[Releases](../../releases)**.

## Installieren

1. Unter [Releases](../../releases) die neueste `DayDrop-x.y.z.apk` laden.
2. Auf dem Handy öffnen. Android fragt einmal nach der Erlaubnis, Apps aus
   dieser Quelle zu installieren – das ist normal bei APKs außerhalb des Play
   Store.
3. Ab Android 8 (API 26) aufwärts.

Alle Releases sind mit demselben Schlüssel signiert, spätere Versionen
installieren sich also als Update über die vorherige.

## Was drin ist

| Karte | Inhalt |
| --- | --- |
| 📅 Heute ist … | Kuriose und offizielle Aktionstage, 157 Tage kuratiert, der Rest live von Wikipedia |
| ⏳ Heute vor X Jahren | Zwei Ereignisse aus verschiedenen Epochen, live von Wikipedia inkl. Bild |
| 🤯 Fact of the Day | Überraschende Fakten |
| 🌍 Wusstest du? | Länder, Menschen, Natur, Kultur |
| 🔬 Science Drop | Weltraum, Tiere, Körper, Technik |
| 🎬 Pop Culture | Filme, Games, Musik, Internet – bevorzugt mit Bezug zum Datum |
| 🧠 Mini-Frage | Erst tippen, dann Auflösung mit Erklärung |
| 🎲 Random Drop | Komplett zufällig |

Dazu: **Streak** (🔥 Tage in Folge), **Speichern** in eine eigene Liste,
**Teilen** als 1080×1920-Story-Karte, und eine **Push-Nachricht** pro Tag, die
neugierig macht, ohne die Karte zu verraten.

### Personalisierung

Beim ersten Start wählt man mindestens drei Themen aus fünfzehn (Geschichte,
Wissenschaft, Weltraum, Tiere, Natur, Körper, Technik, Geografie, Sport,
Gaming, Musik, Filme, Essen, Sprache, Weird Facts). Drei der Karten pro Tag
kommen dann aus diesen Themen und sind mit **FÜR DICH** markiert. Änderbar
jederzeit in den Einstellungen.

## Wie der Feed gebaut wird

- **Deterministisch pro Tag.** Dasselbe Datum ergibt immer denselben Feed – die
  App mischt die Karten nicht neu, wenn man sie zwischendurch schließt.
- **Keine Wiederholungen.** Jeder Pool wird als feste, einmal gemischte
  Permutation durchlaufen, pro Tag um genau so viele Karten weiter, wie der Slot
  verbraucht. Zwischen zwei Auftritten derselben Karte liegen damit exakt
  `Poolgröße / Karten pro Tag` Tage – bei breit gewählten Themen über vier
  Wochen. Das ist in `DropGeneratorTest` festgenagelt.
- **Getrennte Pools.** Personalisierte und allgemeine Karten ziehen aus
  disjunkten Mengen, eine Karte kann also nie in beiden Rollen auftauchen.
- **Datumsgebundenes bleibt beim Datum.** Einträge mit `date` (z. B. der
  Kinostart von Star Wars) erscheinen nur an ihrem Tag, nie zufällig dazwischen.

## Daten und Netz

Alles Persönliche – Themen, Streak, Favoriten, Uhrzeit der Erinnerung – bleibt
per DataStore auf dem Gerät. Es gibt kein Konto, kein Backend, kein Tracking.

Die einzigen Netzaufrufe gehen an die offene Wikimedia-REST-API (`onthisday` für
historische Ereignisse und Feiertage, `page/summary` für Bilder). Ohne Netz
funktioniert die App weiter: die 234 Fakten, 157 Aktionstage und 60 Fragen
liegen in der APK, und für Geschichte gibt es hinterlegte Fallbacks.

Bilder stammen damit aus Wikipedia/Wikimedia Commons und werden zur Laufzeit
geladen, nicht mitgeliefert.

## Selbst bauen

```bash
git clone https://github.com/gravijet/daydrop.git
cd daydrop
echo "sdk.dir=/pfad/zum/android-sdk" > local.properties

./gradlew testDebugUnitTest    # 24 Tests, kein Netz nötig
./gradlew assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
```

Gebraucht werden JDK 17 und das Android SDK mit Plattform 35.

### Neues Release veröffentlichen

`versionCode` und `versionName` in `app/build.gradle.kts` hochziehen, dann:

```bash
git tag v1.0.1 && git push origin v1.0.1
```

Der Workflow `.github/workflows/release.yml` baut, testet, signiert und hängt
die APK an ein GitHub-Release. Alternativ von Hand über den Actions-Tab.

### Signierung

Der Release-Key liegt als `app/daydrop-release.jks` im Repo. Für eine private
App, die nie im Play Store landet, ist das der pragmatische Weg: jeder Build –
lokal wie in der CI – erzeugt eine APK, die sich über die vorherige installiert.
Wer den Key austauschen will, setzt `DAYDROP_KEYSTORE`,
`DAYDROP_KEYSTORE_PASSWORD`, `DAYDROP_KEY_ALIAS` und `DAYDROP_KEY_PASSWORD` als
Umgebungsvariablen – der Build nimmt sie dann statt des mitgelieferten Keys.

## Inhalte erweitern

Die Inhalte liegen als JSON in `app/src/main/assets/content/`:

- `facts.json` – `kind` ist eines von `fact`, `know`, `science`, `pop`,
  `random`, `history`. Optional: `date` (`MM-TT`, bindet den Eintrag an einen
  Tag) und `wiki` (Artikelname, aus dem das Titelbild geladen wird).
- `days.json` – nach `MM-TT` geschlüsselte Aktionstage.
- `quiz.json` – Frage, Antwortoptionen, `answerIndex`, Erklärung.

`ContentTest` prüft beim Testlauf Struktur, Themen-IDs, Antwortindizes und ob
jedes Thema genug Material für die Personalisierung hat – ein Tippfehler fällt
also im Build auf, nicht erst auf dem Handy.

## Technik

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · DataStore ·
WorkManager · Coil · kotlinx.serialization · minSdk 26 · targetSdk 35 ·
R8 aktiv, APK ~1,7 MB.
