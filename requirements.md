# Plan aplikacji Android do obsługi przyjęcia dostawy

## 1. Cel aplikacji

Aplikacja Android ma wspierać pracownika sklepu lub magazynu w przyjmowaniu dostawy poprzez skanowanie kodów kreskowych produktów i deklarowanie liczby faktycznie dostarczonych sztuk. Aplikacja działa w tej samej sieci Wi-Fi co aplikacja PC i synchronizuje z nią bieżący stan obecnej dostawy.

System ma być prosty, szybki i odporny na chwilowe problemy z łącznością. Najważniejszy jest aktualny stan obecnej dostawy, a nie rozbudowana historia operacji.

## 2. Główne założenia biznesowe

Na PC działa aplikacja, do której operator ładuje dokument dostawy. Dokument zawiera co najmniej kod kreskowy produktu, nazwę produktu i oczekiwaną liczbę sztuk.

Aplikacja Android:

* może pobrać aktualną dostawę z PC,
* pozwala skanować produkty,
* pozwala wpisać ilość ręcznie,
* domyślnie ustawia ilość na 1,
* udostępnia szybkie akcje +1, +2, +4 i ALL,
* pozwala wprowadzać wartości ujemne w celu korekty,
* po zatwierdzeniu aktualizuje swój lokalny stan,
* wysyła stan na PC asynchronicznie, bez blokowania użytkownika.

Na PC:

* aktualizuje się stan dostawy,
* możliwa jest weryfikacja danych konkretnego telefonu względem danych zapisanych na PC,
* generowany jest raport PDF braków, nadwyżek i produktów niezamówionych.

## 3. Zakres aplikacji Android

Aplikacja Android odpowiada za:

* pobranie aktualnej dostawy z PC,
* lokalne przechowywanie danych bieżącej dostawy,
* skanowanie kodów kreskowych,
* edycję ilości dla produktu,
* lokalne zapisywanie końcowej ilości dla danego produktu na danym urządzeniu,
* synchronizację z PC,
* weryfikację zgodności lokalnych danych z danymi zapisanymi na PC dla tego samego urządzenia,
* identyfikację urządzenia i podstawowy audyt operacyjny,
* czyszczenie danych lokalnych.

Aplikacja Android nie odpowiada za:

* generowanie raportu PDF,
* zarządzanie wieloma magazynami,
* rozbudowane uprawnienia użytkowników,
* zaawansowaną historię zmian.

# 4. Model działania

## 4.1. Zasada synchronizacji

Najważniejsza decyzja projektowa: telefon nie wysyła zdarzeń typu „dodaj +1”, tylko wysyła aktualny stan końcowy dla produktu na tym urządzeniu.

Przykład:

* pracownik zeskanował produkt A i ustawił 3,
* później skorygował do 2,
* telefon przechowuje wartość 2,
* do PC wysyła: dla urządzenia X i kodu Y aktualna ilość wynosi 2.

Dzięki temu:

* duplikaty requestów nie zawyżają liczb,
* kolejność requestów ma mniejsze znaczenie,
* retry jest bezpieczniejsze,
* agregacja na PC jest prostsza.

## 4.2. Agregacja na PC

PC przechowuje stan per urządzenie i produkt:

* deviceId + barcode -> quantity

Wynik globalny dla produktu:

* suma quantity ze wszystkich urządzeń dla danego barcode

To pozwala:

* obsłużyć 1–3 skanery,
* zweryfikować pojedynczy telefon,
* zachować prostotę.

# 5. Użytkownicy i scenariusze

## 5.1. Typ użytkownika

Jedyny realny użytkownik aplikacji Android to pracownik przyjmujący towar.

## 5.2. Główne scenariusze użycia

### Scenariusz A: pobranie aktualnej dostawy

1. Użytkownik uruchamia aplikację.
2. Aplikacja wykrywa lub ma skonfigurowany adres PC.
3. Użytkownik wybiera „Pobierz aktualną dostawę”.
4. Aplikacja pobiera listę produktów.
5. Dane zostają zapisane lokalnie.

### Scenariusz B: skan i zatwierdzenie ilości

1. Użytkownik skanuje kod kreskowy.
2. Aplikacja wyszukuje produkt lokalnie.
3. Pokazuje kartę produktu.
4. Domyślna ilość to 1.
5. Użytkownik może wybrać +1, +2, +4, ALL albo wpisać wartość ręcznie.
6. Użytkownik zatwierdza.
7. Aplikacja zapisuje końcową ilość lokalną dla produktu.
8. Aplikacja oznacza rekord jako wymagający synchronizacji.
9. Aplikacja wysyła stan do PC asynchronicznie.

### Scenariusz C: produkt spoza listy

1. Użytkownik skanuje kod, którego nie ma w dostawie.
2. Aplikacja wyświetla ostrzeżenie.
3. Użytkownik może mimo to wpisać ilość i zatwierdzić.
4. Produkt zapisuje się jako spoza dostawy.
5. Synchronizacja do PC działa normalnie.

### Scenariusz D: korekta

1. Użytkownik otwiera produkt już wcześniej zapisany.
2. Wprowadza nową wartość lub używa wartości ujemnej do korekty.
3. Aplikacja aktualizuje końcowy stan produktu na urządzeniu.
4. Zmieniony stan jest ponownie synchronizowany.

### Scenariusz E: weryfikacja zgodności z PC

1. Użytkownik wybiera „Sprawdź zgodność z PC”.
2. Aplikacja pobiera z PC stan zapisany dla tego deviceId.
3. Porównuje każdą lokalną pozycję z danymi na PC.
4. Wyświetla różnice, jeśli występują.

# 6. Wymagania funkcjonalne

## 6.1. Identyfikacja urządzenia

Aplikacja musi generować trwały `deviceId` przy pierwszym uruchomieniu. Identyfikator ma być zachowany między restartami aplikacji.

Aplikacja musi pozwalać ustawić czytelną nazwę urządzenia, np.:

* Skaner 1
* Telefon magazyn
* Telefon Anna

Ta nazwa ma być wysyłana do PC wraz z synchronizacją.

## 6.2. Pobieranie bieżącej dostawy

Aplikacja musi umożliwiać pobranie aktualnej dostawy z PC.

Pobrana dostawa powinna zawierać:

* identyfikator dostawy,
* datę lub znacznik wersji,
* listę produktów,
* dla każdego produktu: barcode, name, expectedQty.

Po pobraniu aplikacja zapisuje dane lokalnie.

Jeśli na urządzeniu istnieją już dane poprzedniej dostawy, aplikacja ma wymusić jedną z decyzji:

* nadpisz bieżące dane,
* anuluj pobranie.

## 6.3. Lokalna baza danych

Aplikacja musi posiadać lokalną bazę SQLite, najlepiej przez Room.

W bazie mają być przechowywane:

* konfiguracja urządzenia,
* bieżąca dostawa,
* lokalny stan produktów dla tego urządzenia,
* status synchronizacji.

Nie ma potrzeby przechowywania pełnej historii wszystkich zmian, ale baza ma przechowywać końcowy aktualny stan dla produktu oraz podstawowe metadane synchronizacyjne.

## 6.4. Skanowanie kodów kreskowych

Aplikacja musi umożliwiać skanowanie kodów kreskowych aparatem telefonu.

Obsługiwane formaty powinny obejmować co najmniej najczęstsze formaty handlowe:

* EAN-13
* EAN-8
* Code 128
* UPC-A, jeśli biblioteka wspiera

Po zeskanowaniu kodu aplikacja:

* wyszukuje produkt w lokalnej dostawie,
* otwiera ekran potwierdzenia ilości,
* informuje, czy produkt jest znany.

## 6.5. Wprowadzanie ilości

Dla każdego skanu aplikacja ma pokazać:

* barcode,
* nazwę produktu lub informację „produkt spoza dostawy”,
* oczekiwaną ilość z dokumentu, jeśli istnieje,
* aktualną ilość lokalną na tym urządzeniu,
* numeryczny input.

Domyślna wartość w inputcie: 1.

Muszą istnieć szybkie przyciski:

* +1
* +2
* +4
* ALL

Przycisk ALL:

* jest aktywny tylko wtedy, gdy produkt znajduje się w pobranej dostawie,
* ustawia wartość odpowiadającą całej oczekiwanej ilości dla produktu z dokumentu,
* aplikacja musi jasno poinformować, że jest to wartość ustawiana na tym urządzeniu, a nie globalnie dla wszystkich skanerów.

Aplikacja musi pozwalać wpisywać wartości ujemne do korekt.

Po zatwierdzeniu nie zapisujemy „delta”, tylko końcową wartość produktu na tym telefonie.

## 6.6. Zapisywanie końcowego stanu produktu

Dla każdego produktu na urządzeniu przechowywany jest rekord zawierający:

* barcode,
* name,
* quantity,
* fromDelivery,
* updatedAt,
* syncStatus,
* remoteQuantityLastSeen,
* sequenceNumber lub revision.

Każde zatwierdzenie produktu nadpisuje lokalny stan końcowy.

## 6.7. Obsługa produktów spoza listy dostawy

Jeśli zeskanowany barcode nie występuje w pobranej dostawie:

* aplikacja musi to wyraźnie zaznaczyć,
* użytkownik nadal może zapisać ilość,
* produkt zapisuje się z flagą `fromDelivery = false`.

ALL dla takiego produktu ma być niedostępne.

## 6.8. Synchronizacja z PC

Synchronizacja ma działać asynchronicznie. Użytkownik nie może czekać na odpowiedź serwera po każdym zatwierdzeniu.

Po zapisaniu lokalnym:

* rekord otrzymuje status `PENDING_SYNC`,
* aplikacja próbuje wysłać go natychmiast w tle,
* jeśli wysyłka się nie powiedzie, rekord pozostaje w kolejce retry.

Synchronizacja ma wysyłać pełny aktualny stan produktu dla danego urządzenia.

Przykładowy payload:

```json
{
  "deliveryId": "DELIVERY-2026-04-21-01",
  "deviceId": "d2b1f5aa-9a12-4b7a-8d8c-123456789abc",
  "deviceName": "Skaner 1",
  "barcode": "5901234123457",
  "name": "Cola 0.5L",
  "quantity": 7,
  "fromDelivery": true,
  "updatedAt": "2026-04-21T10:15:30",
  "revision": 18
}
```

## 6.9. Retry i bezpieczeństwo przed duplikatami

Aplikacja ma ponawiać synchronizację rekordów ze statusem `PENDING_SYNC` lub `FAILED_SYNC`.

Retry może działać:

* po każdej zmianie,
* po odzyskaniu sieci,
* okresowo, np. co kilka sekund, gdy są rekordy oczekujące.

Ponieważ wysyłany jest stan końcowy, a nie zdarzenie przyrostowe, wielokrotne wysłanie tego samego rekordu nie może zmieniać końcowego wyniku na PC.

## 6.10. Ochrona przed nadpisaniem nowszych danych starszymi

Każdy rekord produktu musi posiadać `revision` zwiększane lokalnie przy każdej zmianie.

Na PC aktualizacja dla `deviceId + barcode` może zostać przyjęta tylko wtedy, gdy:

* revision jest większe od poprzednio zapisanego,
  albo
* revision jest równe, ale updatedAt jest nowsze.

Aplikacja Android musi utrzymywać revision lokalnie.

## 6.11. Weryfikacja zgodności z PC

Aplikacja musi umożliwiać sprawdzenie, czy stan lokalny danego telefonu zgadza się z danymi zapisanymi na PC.

Po uruchomieniu tej funkcji:

* aplikacja pobiera z PC stan tylko dla własnego `deviceId`,
* porównuje każdą pozycję lokalną z pozycją po stronie serwera,
* oznacza pozycje jako zgodne lub rozbieżne.

Wynik ma zawierać:

* liczbę zgodnych pozycji,
* liczbę rozbieżnych pozycji,
* listę różnic.

## 6.12. Podgląd lokalnego stanu

Aplikacja musi mieć ekran listy pozycji zapisanych na tym urządzeniu.

Dla każdej pozycji powinny być widoczne:

* nazwa,
* barcode,
* current quantity,
* znacznik „z dostawy” lub „spoza dostawy”,
* status synchronizacji.

Lista powinna pozwalać:

* wyszukać produkt,
* otworzyć szczegóły,
* poprawić ilość.

## 6.13. Czyszczenie danych

Aplikacja musi mieć przycisk czyszczenia danych lokalnych.

Czyszczenie powinno usuwać:

* pobraną dostawę,
* lokalny stan produktów,
* kolejkę synchronizacji.

Czyszczenie nie powinno usuwać:

* deviceId,
* deviceName,
  chyba że użytkownik wejdzie w ustawienia zaawansowane.

## 6.14. Obsługa stanu sieci

Aplikacja musi wykrywać podstawowy stan połączenia z serwerem PC:

* połączono,
* brak odpowiedzi,
* brak sieci.

Informacja ta powinna być widoczna na ekranie głównym.

## 6.15. Audyt operacyjny

Aplikacja musi zawsze wysyłać:

* deviceId,
* deviceName,
* updatedAt,
* revision.

To pozwala PC przypisać stan do konkretnego telefonu i wyświetlić, kto wprowadził daną ilość.

# 7. Wymagania niefunkcjonalne

## 7.1. Prostota

Interfejs ma być maksymalnie prosty. Użytkownik ma wykonać typowy skan i zatwierdzenie w minimalnej liczbie kroków.

## 7.2. Szybkość działania

Po zeskanowaniu produktu ekran edycji musi pojawić się niemal natychmiast. Zapis lokalny nie może zależeć od odpowiedzi z sieci.

## 7.3. Odporność na utratę połączenia

Aplikacja musi działać dalej przy chwilowym braku sieci. Dane muszą zostać zachowane lokalnie i zsynchronizowane później.

## 7.4. Spójność danych

Dla każdego `deviceId + barcode` na urządzeniu istnieje jedna aktualna wartość końcowa. System nie może liczyć tej samej wartości wielokrotnie wskutek retry.

## 7.5. Czytelność komunikatów

Komunikaty o błędach i ostrzeżeniach muszą być zrozumiałe dla użytkownika nietechnicznego.

## 7.6. Stabilność

Aplikacja nie może tracić lokalnych danych przy zwykłym zamknięciu, minimalizacji lub rotacji ekranu.

## 7.7. Minimalne wymagania sprzętowe

Aplikacja powinna działać na typowych firmowych telefonach z Androidem 10+.

## 7.8. Bezpieczeństwo lokalne

Lokalna baza nie wymaga silnego szyfrowania klasy enterprise, ale należy unikać przechowywania zbędnych danych wrażliwych. System nie operuje na danych osobowych wysokiego ryzyka.

## 7.9. Użyteczność offline-first

Priorytetem jest najpierw zapis lokalny, potem synchronizacja.

## 7.10. Konfigurowalność

Adres serwera PC ma być możliwy do ustawienia ręcznie. Opcjonalnie można dodać prosty mechanizm wykrywania serwera w sieci lokalnej, ale ręczna konfiguracja musi pozostać dostępna.

# 8. Architektura aplikacji Android

## 8.1. Stos technologiczny

Rekomendowany stack:

* Kotlin
* Jetpack Compose
* MVVM
* Room
* Retrofit
* OkHttp
* WorkManager
* CameraX
* ML Kit Barcode Scanning albo ZXing

## 8.2. Architektura warstwowa

### Warstwa UI

Ekrany Compose, ViewModel, stan UI.

### Warstwa domenowa

Use case’y:

* FetchCurrentDelivery
* ScanBarcode
* SaveProductQuantity
* SyncPendingStates
* VerifyDeviceStateWithServer
* ClearLocalData

### Warstwa danych

Repozytoria:

* DeliveryRepository
* ProductStateRepository
* SyncRepository
* SettingsRepository

Źródła danych:

* Room
* REST API do PC
* lokalne ustawienia przez DataStore

# 9. Model danych lokalnych

## 9.1. Tabela device_settings

* deviceId: String
* deviceName: String
* serverBaseUrl: String
* lastDeliveryId: String?

## 9.2. Tabela delivery_info

* deliveryId: String
* deliveryVersion: String?
* importedAt: Long

## 9.3. Tabela delivery_items

* barcode: String
* name: String
* expectedQty: Int
* deliveryId: String

## 9.4. Tabela local_product_state

Klucz główny: barcode

Pola:

* barcode: String
* name: String?
* quantity: Int
* fromDelivery: Boolean
* expectedQty: Int?
* revision: Long
* updatedAt: Long
* syncStatus: String
* remoteQuantityLastSeen: Int?
* lastSyncAttemptAt: Long?
* lastSyncSuccessAt: Long?

Uwaga: ponieważ na jednym telefonie przechowujemy tylko stan bieżącej dostawy, klucz barcode jest wystarczający. Jeśli dopuszczamy wiele dostaw lokalnie, klucz powinien być złożony z deliveryId + barcode. Tu nie ma takiej potrzeby.

# 10. API wymagane po stronie PC

Aplikacja Android zakłada istnienie poniższych endpointów.

## 10.1. Pobranie aktualnej dostawy

`GET /api/delivery/current`

Odpowiedź:

```json
{
  "deliveryId": "DELIVERY-2026-04-21-01",
  "version": "v1",
  "items": [
    {
      "barcode": "5901234123457",
      "name": "Cola 0.5L",
      "expectedQty": 20
    }
  ]
}
```

## 10.2. Zapis stanu produktu z urządzenia

`POST /api/device-state`

Payload jak w sekcji synchronizacji.

Odpowiedź:

```json
{
  "accepted": true,
  "serverQuantity": 7,
  "serverRevision": 18
}
```

albo:

```json
{
  "accepted": false,
  "reason": "STALE_REVISION",
  "serverQuantity": 9,
  "serverRevision": 22
}
```

## 10.3. Pobranie stanu dla urządzenia

`GET /api/device-state/{deviceId}`

Odpowiedź:

```json
{
  "deviceId": "d2b1f5aa-9a12-4b7a-8d8c-123456789abc",
  "deviceName": "Skaner 1",
  "items": [
    {
      "barcode": "5901234123457",
      "quantity": 7,
      "revision": 18,
      "updatedAt": "2026-04-21T10:15:30"
    }
  ]
}
```

## 10.4. Test połączenia

`GET /api/health`

Odpowiedź:

```json
{
  "status": "UP"
}
```

# 11. Ekrany aplikacji

## 11.1. Ekran startowy

Elementy:

* status połączenia z PC,
* nazwa urządzenia,
* adres serwera,
* przycisk „Pobierz aktualną dostawę”,
* przycisk „Skanuj produkt”,
* przycisk „Moje pozycje”,
* przycisk „Sprawdź zgodność z PC”,
* przycisk „Ustawienia”.

## 11.2. Ekran skanera

Elementy:

* podgląd z kamery,
* ramka skanowania,
* przycisk cofnięcia,
* opcjonalnie ręczne wpisanie kodu.

Po odczycie kodu automatyczne przejście do ekranu szczegółu produktu.

## 11.3. Ekran produktu

Elementy:

* nazwa produktu lub komunikat „spoza dostawy”,
* barcode,
* expectedQty, jeśli znane,
* aktualna ilość lokalna,
* pole numeryczne,
* przyciski +1, +2, +4, ALL,
* przycisk zatwierdzenia,
* przycisk anulowania,
* wskaźnik synchronizacji.

## 11.4. Ekran listy lokalnych pozycji

Elementy:

* pole wyszukiwania,
* lista pozycji,
* badge statusu synchronizacji,
* możliwość edycji po kliknięciu.

## 11.5. Ekran weryfikacji zgodności

Elementy:

* liczba zgodnych pozycji,
* liczba niezgodnych pozycji,
* lista różnic:

    * barcode
    * lokalnie
    * na PC
    * status

## 11.6. Ekran ustawień

Elementy:

* deviceName,
* serverBaseUrl,
* przycisk testu połączenia,
* przycisk czyszczenia danych lokalnych,
* opcjonalnie podgląd deviceId.

# 12. Logika biznesowa kluczowych funkcji

## 12.1. Zatwierdzanie ilości produktu

Po wejściu na ekran produktu:

* jeśli produkt istnieje lokalnie, pole ilości pokazuje jego obecną quantity,
* jeśli produkt jest nowy, pole startuje od 1.

Po kliknięciu zatwierdź:

1. walidacja liczby,
2. zapis do `local_product_state`,
3. revision = revision + 1,
4. updatedAt = now,
5. syncStatus = PENDING_SYNC,
6. uruchomienie zadania synchronizacji.

## 12.2. Działanie przycisku ALL

Jeśli produkt znajduje się w dostawie:

* aplikacja pobiera expectedQty z lokalnej dostawy,
* ustawia pole ilości na expectedQty.

Ważne: ponieważ telefon przechowuje końcowy stan tego urządzenia, ALL ustawia ilość produktu na tym urządzeniu równą całej ilości oczekiwanej z dokumentu. W interfejsie trzeba to jasno opisać.

Jeśli uznasz później, że to zbyt ryzykowne przy 2–3 telefonach, można łatwo zmienić semantykę ALL na „ustaw brakującą ilość względem lokalnej pozycji telefonu”, ale w obecnym planie przyjmuję prostszy wariant zgodny z Twoim opisem.

## 12.3. Obsługa odpowiedzi z PC

Jeżeli odpowiedź jest poprawna i accepted=true:

* syncStatus = SYNCED,
* remoteQuantityLastSeen = serverQuantity,
* lastSyncSuccessAt = now

Jeżeli odpowiedź zwróci `STALE_REVISION`:

* aplikacja oznacza rekord jako CONFLICT,
* przy następnym wejściu użytkownik widzi komunikat o niezgodności,
* rekord trafia też do ekranu weryfikacji.

## 12.4. Weryfikacja zgodności

Porównanie wykonujemy na parach:

* lokalne quantity
* quantity na PC dla tego samego deviceId i barcode

Statusy:

* MATCH
* MISSING_ON_SERVER
* DIFFERENT_VALUE
* ONLY_ON_SERVER

# 13. Walidacje

Aplikacja musi walidować:

* czy serverBaseUrl nie jest pusty,
* czy pobrana dostawa ma poprawny format,
* czy input ilości jest liczbą całkowitą,
* czy barcode po skanie nie jest pusty,
* czy przycisk ALL nie jest używany dla produktu spoza dostawy.

Aplikacja nie blokuje wartości ujemnych.

Aplikacja może opcjonalnie ostrzegać przy bardzo dużych liczbach, np. powyżej 999.

# 14. Obsługa błędów

## 14.1. Brak połączenia

Komunikat:
„Brak połączenia z komputerem. Dane zapisano lokalnie i zostaną zsynchronizowane później.”

## 14.2. Błąd pobrania dostawy

Komunikat:
„Nie udało się pobrać aktualnej dostawy.”

## 14.3. Produkt spoza dostawy

Komunikat:
„Produkt nie znajduje się w bieżącej dostawie. Możesz mimo to zapisać ilość.”

## 14.4. Konflikt wersji

Komunikat:
„Stan produktu na komputerze różni się od stanu telefonu. Sprawdź zgodność danych.”

# 15. Synchronizacja w tle

Do synchronizacji należy użyć WorkManager.

Zadania:

* jednorazowe po zmianie produktu,
* okresowe przy oczekujących rekordach,
* uruchamiane po odzyskaniu sieci.

Strategia:

* pobierz rekordy `PENDING_SYNC`, `FAILED_SYNC`, `CONFLICT`,
* dla każdego wyślij aktualny stan,
* po sukcesie oznacz `SYNCED`,
* po błędzie ustaw `FAILED_SYNC`.

# 16. Wydajność i UX

Aplikacja ma być zoptymalizowana pod częste powtarzalne operacje. Priorytet:

* minimalna liczba kliknięć,
* brak czekania na serwer,
* szybkie przejście po skanie do potwierdzenia,
* czytelne przyciski ilości,
* duży font i prosty układ.

Warto dodać:

* krótką wibrację lub dźwięk po poprawnym skanie,
* kolorowe oznaczenie produktu znanego i spoza dostawy,
* badge statusu synchronizacji.

# 17. Plan implementacji dla Gemini

## Etap 1. Fundament

* utworzyć projekt Android w Kotlinie,
* skonfigurować Compose, Room, Retrofit, WorkManager, CameraX,
* stworzyć DataStore dla ustawień,
* wdrożyć generowanie deviceId.

## Etap 2. Model danych

* encje Room,
* DAO,
* repozytoria,
* modele DTO do API.

## Etap 3. Pobieranie dostawy

* ekran ustawień serwera,
* endpoint health,
* endpoint current delivery,
* zapis do bazy lokalnej.

## Etap 4. Skaner

* integracja CameraX i biblioteki barcode,
* ekran skanera,
* przekierowanie do szczegółu produktu.

## Etap 5. Ekran produktu

* formularz ilości,
* przyciski +1, +2, +4, ALL,
* zapis lokalny,
* status synchronizacji.

## Etap 6. Synchronizacja

* POST device-state,
* retry,
* obsługa revision,
* WorkManager.

## Etap 7. Lista pozycji i weryfikacja

* ekran listy,
* ekran porównania z PC,
* obsługa rozbieżności.

## Etap 8. Czyszczenie i dopracowanie

* clear local data,
* dopracowanie komunikatów,
* testy ręczne i testy jednostkowe.

# 18. Kryteria akceptacji

Aplikacja zostaje uznana za gotową, jeżeli:

* potrafi pobrać aktualną dostawę z PC,
* potrafi zeskanować kod kreskowy,
* pozwala ustawić ilość przez input i szybkie przyciski,
* zapisuje końcową lokalną wartość produktu,
* wysyła tę wartość na PC asynchronicznie,
* zachowuje dane lokalnie przy braku sieci,
* pozwala porównać lokalny stan z danymi na PC dla bieżącego urządzenia,
* poprawnie obsługuje produkty spoza dostawy,
* pozwala wyczyścić dane lokalne.

# 19. Instrukcja dla Gemini

Możesz przekazać Gemini ten skrót zadania:

„Zbuduj aplikację Android w Kotlin + Jetpack Compose do przyjęcia dostawy. Aplikacja działa w tej samej sieci Wi-Fi co aplikacja PC. Musi pobierać bieżącą dostawę z endpointu REST, skanować kody kreskowe, pozwalać użytkownikowi ustawić ilość produktu przez input oraz szybkie przyciski +1, +2, +4 i ALL, zapisywać lokalnie końcową wartość ilości dla produktu na tym urządzeniu, a następnie wysyłać asynchronicznie na PC aktualny stan dla pary deviceId + barcode. Użyj Room, Retrofit, WorkManager, CameraX i architektury MVVM. Dodaj weryfikację zgodności lokalnych danych telefonu z danymi zapisanymi na PC dla tego samego urządzenia. Uwzględnij produkty spoza dostawy, obsługę offline, retry synchronizacji oraz revision chroniący przed nadpisaniem nowszych danych starszymi.”

# 20. Weryfikacja spójności planu

Sprawdziłem plan pod kątem logiki, zgodności z Twoimi założeniami i potencjalnych niespójności.

## 20.1. Czy model „stan końcowy zamiast eventów” jest zachowany

Tak. W całym planie aplikacja Android zapisuje i wysyła końcową quantity dla produktu na urządzeniu, a nie pojedyncze zdarzenia.

## 20.2. Czy rozwiązano problem duplikatów requestów

Tak. Ponieważ PC dostaje aktualny stan końcowy i przechowuje wartość dla `deviceId + barcode`, wielokrotne wysłanie tego samego payloadu nie zawyża sum.

## 20.3. Czy uwzględniono audyt

Tak. Dodano `deviceId`, `deviceName`, `updatedAt`, `revision` oraz ekran weryfikacji zgodności.

## 20.4. Czy uwzględniono możliwość kilku skanerów

Tak. Model PC zakłada agregację po urządzeniach, a aplikacja Android wysyła stan identyfikowany przez urządzenie.

## 20.5. Czy ALL jest jednoznacznie opisane

Tak, ale tu jest jedno miejsce wymagające świadomej decyzji.

W obecnym planie ALL ustawia quantity na tym urządzeniu równe całej oczekiwanej ilości z dokumentu. To jest zgodne z prostotą, ale przy 2–3 urządzeniach może prowadzić do nadmiaru, jeśli dwa telefony użyją ALL dla tego samego produktu.

To nie jest błąd logiczny planu, ale ryzyko operacyjne.

## 20.6. Korekta do planu: doprecyzowanie ALL

Wprowadzam poprawkę:

Przycisk ALL powinien domyślnie ustawiać wartość równą oczekiwanej ilości z dokumentu, ale aplikacja musi przed zatwierdzeniem wyświetlić krótką informację:
„ALL ustawi pełną ilość tego produktu na tym urządzeniu.”

Dodatkowo rekomendacja implementacyjna:
w ustawieniach można przewidzieć tryb alternatywny ALL w przyszłości, bez zmiany architektury.

## 20.7. Czy czyszczenie danych nie psuje identyfikacji urządzenia

Tak. Plan zostawia deviceId i deviceName po zwykłym czyszczeniu.

## 20.8. Czy plan wymaga historii, której nie chcesz

Nie. Plan przechowuje tylko stan bieżący, metadane synchronizacji i minimum potrzebne do porównania z PC.

# 21. Finalna poprawiona wersja decyzji krytycznych

Na koniec zbieram najważniejsze decyzje projektowe, już po weryfikacji:

1. Telefon zapisuje końcową ilość produktu, nie zdarzenia.
2. Synchronizacja jest async i offline-first.
3. Deduplikacja wynika z modelu „deviceId + barcode -> current quantity”.
4. Przed nadpisaniem starszymi danymi chroni `revision`.
5. Produkty spoza dostawy są dozwolone i oznaczane flagą.
6. Audyt opiera się na `deviceId` i `deviceName`.
7. ALL pozostaje dostępne, ale musi jasno informować, że dotyczy stanu tego telefonu.
8. Po zwykłym czyszczeniu danych urządzenie zachowuje swoją tożsamość.

Jeżeli chcesz, mogę w następnym kroku przerobić to na jeszcze bardziej techniczny dokument w formie:

* backlogu z user stories i acceptance criteria,
* specyfikacji API JSON,
* schematu bazy Room,
* albo gotowego promptu produkcyjnego dla Gemini 1.5/2.5 do generowania kodu.
