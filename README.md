# DayDrop

Jeden Morgen sieben Karten: ein kurioser Tag, ein Ereignis von heute vor X
Jahren, ein Artikel des Tages, mehrere Fakten zu deinen Themen und eine
Frage, bei der man erst raten muss. In ein paar Minuten durchgeswipet.

Android-App, privat gebaut. Fertige APK: **[Releases](../../releases)**.

## Installieren

1. Unter [Releases](../../releases) die neueste `DayDrop-x.y.z.apk` laden.
2. Auf dem Handy öffnen. Android fragt einmal nach der Erlaubnis, Apps aus
   dieser Quelle zu installieren – das ist normal bei APKs außerhalb des Play
   Store.
3. Ab Android 8 (API 26) aufwärts.

Alle Releases sind mit demselben Schlüssel signiert, spätere Versionen
installieren sich also als Update über die vorherige.

## Der Tag in sieben Karten

| # | Karte | Woher |
| --- | --- | --- |
| 1 | 📅 **Heute ist …** | Alle 366 Tage kuratiert, Wikipedia als stiller Fallback |
| 2 | ⏳ **Heute vor X Jahren** | Wikipedia, das Ereignis des Tages |
| 3 | 📖 **Artikel des Tages** | Wikipedia; ohne Netz springt ein kuratierter Fakt ein |
| 4 | ✨ **Für dich** | Kuratierter Fakt aus deinen Themen |
| 5–6 | 💡 **Weitere Fakten** | Kuratiert, aus dem restlichen Themen-Vorrat |
| 7 | 🧠 **Mini-Frage** | Erst tippen, dann Auflösung mit Erklärung |

Dazu: **Streak** (🔥 Tage in Folge), **Speichern** in eine eigene Liste,
**Teilen** als 1080×1920-Story-Karte, und eine **Push-Nachricht** pro Tag, die
neugierig macht, ohne die Karte zu verraten. Auf großen Displays bleiben Feed,
Kopfzeile und Aktionen in einer angenehmen Lesespalte statt unruhig in die Breite
zu laufen.

### Personalisierung

Beim ersten Start wählt man mindestens drei Themen aus fünfzehn (Geschichte,
Wissenschaft, Weltraum, Tiere, Natur, Körper, Technik, Geografie, Sport,
Gaming, Musik, Filme, Essen, Sprache, Weird Facts). Karte 4 kommt aus diesen
Themen und ist mit **FÜR DICH** markiert. Änderbar jederzeit in den
Einstellungen.

## Sofort da

Beim Öffnen wird nichts geladen. Die App

1. zeigt den Feed, den sie schon hat – vom letzten Besuch oder von der
   Morgen-Erinnerung, die ihn im Hintergrund fertig baut;
2. baut ihn sonst in einem Rutsch aus den mitgelieferten JSON-Dateien, die beim
   App-Start im Hintergrund geparst wurden. Das sind ein paar Listenoperationen
   im Arbeitsspeicher, kein Netz, keine Datei;
3. fragt erst danach Wikipedia und tauscht die beiden dafür vorgesehenen Karten
   aus, sobald die Antwort da ist.

Es gibt deshalb keinen Ladebildschirm mehr. Ohne Netz merkt man außer den
beiden dafür vorgesehenen Karten nichts.

## Keine Wiederholungen – dauerhaft

Die App merkt sich **jede ausgelieferte Karte** dauerhaft per ID und zieht nur
noch aus dem, was übrig ist. Die Zusage hängt damit nicht an Poolgrößen oder
daran, wie oft man die App öffnet:

- Ist ein kuratierter Vorrat aufgebraucht, fängt er **nicht von vorn an** –
  der Slot wechselt auf Wikipedia (Artikel des Tages, weitere Ereignisse des
  Tages), und die Quelle geht nicht aus.
- Nur die Karte **„Heute ist …“** darf wiederkommen: Ein Gedenktag gehört zu
  seinem Datum. Gibt es für ein Datum mehrere Einträge, rotieren sie jährlich.
- Karten mit Datum (etwa der Kinostart von Star Wars) erscheinen **nur an ihrem
  Tag**, nie zufällig dazwischen.

`DropGeneratorTest` fährt fünf Jahre am Stück durch und prüft, dass keine ID
zweimal ausgeliefert wird.

### Wie lange reicht der handgeschriebene Vorrat?

Mit Netz verbraucht ein Tag im Schnitt **drei** kuratierte Fakten-Karten
(„Für dich" plus zwei der weiteren Fakten), weil die Slots „Heute vor X
Jahren" und „Artikel des Tages" von Wikipedia kommen. Rund 2.311 nicht
datumsgebundene Fakten stecken im Vorrat – rechnerisch reicht das über zwei
Jahre, die Fragen (353 Stück) rund elf Monate. Danach übernimmt
Wikipedia diese Slots, ohne Wiederholung. Ohne Netz kommen mehr Karten aus
dem kuratierten Vorrat, weil dann auch der Artikel-Slot lokal gefüllt wird.

Nachlegen geht jederzeit: eine eigene kleine `facts_*.json`-Edition, fertig.

## Bilder

Hinter jeder Karte steht ein Foto aus einem **kuratierten Katalog** von rund
3.400 Wikimedia-Commons-*Featured Pictures*: Landschaften, Tiere, Nachthimmel,
Mineralien, Bibliotheken – nach Thema sortiert und jede URL beim Aufbau des
Katalogs direkt über die Wikimedia-API verifiziert. Das Bild sitzt in voller
Stärke hinter der Karte, nur der untere Rand verdunkelt sich sanft für den
Text – keine Karte zeigt bloß eine Farbfläche.

Was bewusst **nicht** drin ist: Reproduktionen von Drucken, Plakaten und
Gemälden sowie historische Aufnahmen. Genau die tragen ein Datum im Bild und
sahen auf einer Karte über Honig falsch aus. Der Katalog wird beim Bauen über
Dateinamen gefiltert (Jahreszahlen vor 1990, Wörter wie *poster*, *painting*,
*print*).

Ein datumsgebundenes Bild gibt es nur an genau einer Stelle: auf der Karte
„Heute vor X Jahren“ hängt das Bild, das Wikipedia dem Ereignis mitgibt – und
die Karte erscheint ausschließlich an diesem Datum.

Welches Foto eine Karte bekommt, entscheidet ein FNV-Hash über ihre ID. Gleiche
Karte, gleiches Bild – unabhängig von Gerät und Tag.

## Daten und Netz

Alles Persönliche – Themen, Streak, Favoriten, gesehene Karten, Uhrzeit der
Erinnerung – bleibt per DataStore auf dem Gerät. Es gibt kein Konto, kein
Backend, kein Tracking. Neue Fakten erscheinen als kleine, separat prüfbare
Editionen; die **Atlas-Edition** und die neue **Mosaik-Edition** erweitern den
Vorrat um 150 sorgfältig geschriebene Karten aus allen 15 Themenbereichen.

Die Netzaufrufe gehen ausschließlich an die offene Wikimedia-REST-API
(`onthisday` für Ereignisse und Feiertage, `feed/featured` für den Artikel des
Tages) und an `upload.wikimedia.org` für die Fotos. Ohne Netz funktioniert die
App weiter: 2406 Fakten, 366 Aktionstage und 353 Fragen liegen in der APK.

## Selbst bauen

```bash
git clone https://github.com/gravijet/daydrop.git
cd daydrop
echo "sdk.dir=/pfad/zum/android-sdk" > local.properties

./gradlew testDebugUnitTest    # kein Netz nötig
./gradlew assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
```

Gebraucht werden JDK 17 und das Android SDK mit Plattform 35.

### Neues Release veröffentlichen

`versionCode` und `versionName` in `app/build.gradle.kts` hochziehen und pushen
– das reicht. `.github/workflows/release.yml` baut, testet, signiert und hängt
die APK an ein neues GitHub-Release `v<versionName>`.

Existiert für die Version bereits ein Release, macht ein normaler Push nichts:
so wird nie stillschweigend eine APK ersetzt, die schon jemand installiert hat.
Bewusst neu veröffentlichen geht über einen Tag (`git tag v1.1.1 && git push
origin v1.1.1`) oder von Hand über den Actions-Tab.

### Signierung

Der Release-Key liegt als `app/daydrop-release.jks` im Repo. Für eine private
App, die nie im Play Store landet, ist das der pragmatische Weg: jeder Build –
lokal wie in der CI – erzeugt eine APK, die sich über die vorherige installiert.
Wer den Key austauschen will, setzt `DAYDROP_KEYSTORE`,
`DAYDROP_KEYSTORE_PASSWORD`, `DAYDROP_KEY_ALIAS` und `DAYDROP_KEY_PASSWORD` als
Umgebungsvariablen – der Build nimmt sie dann statt des mitgelieferten Keys.

## Inhalte erweitern

Die Inhalte liegen als JSON in `app/src/main/assets/content/`:

- `facts.json` und `facts_*.json` – `kind` ist eines von `fact`, `know`,
  `science`, `pop`, `random`, `history`. Optional: `date` (`MM-TT`, bindet den
  Eintrag an einen Tag), `wiki` (deutscher Wikipedia-Artikel) oder `sourceUrl`
  plus `sourceLabel` für eine direkte Quelle.
- `days.json` – nach `MM-TT` geschlüsselte Aktionstage.
- `quiz.json` – Frage, Antwortoptionen, `answerIndex`, Erklärung.
- `images.json` – Foto-URLs pro Thema, plus `_default`.

`ContentTest` prüft beim Testlauf Struktur, Themen-IDs, Antwortindizes,
Längen, doppelte Titel und Texte, ob jeder Text mindestens zwei Sätze hat und
ob jedes Thema genug Material trägt – ein Tippfehler fällt also im Build auf,
nicht erst auf dem Handy.

## Technik

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · DataStore ·
WorkManager · Coil · kotlinx.serialization · minSdk 26 · targetSdk 35 ·
R8 aktiv.
