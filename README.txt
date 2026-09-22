# Codziennik 2.0

Prywatny, lokalny dziennik codziennych nawyków, notatek i pamiątek.
Motywy aplikacji są wektorowymi i XML-owymi zasobami Androida, dlatego projekt
nie wymaga dodawania plików binarnych do pull requesta.

## Uruchomienie

Otwórz katalog główny w Android Studio (JDK 17) albo uruchom `gradle assembleDebug`.
APK debug znajduje się w `app/build/outputs/apk/debug/app-debug.apk`.

## Dane użytkownika

Aplikacja działa lokalnie. Przy pierwszym uruchomieniu wersja 2.0 importuje wpisy
z poprzedniej wersji zapisane w `SharedPreferences` o nazwie `codziennik`; nie
usuwa poprzednich preferencji. Nowe wpisy są przechowywane w lokalnej bazie SQLite.
