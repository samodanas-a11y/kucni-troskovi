# Kućni troškovi

Jednostavna Android aplikacija na srpskoj latinici za kućni budžet u Srbiji.

## Instalacija

[Preuzmi potpisani APK](https://github.com/samodanas-a11y/kucni-troskovi/releases/latest/download/kucni-troskovi.apk)

Android 8.0 ili noviji. Otvori APK na telefonu i, ako Android zatraži, dozvoli instalaciju iz izabranog pregledača ili upravljača datotekama.

## Mogućnosti

- Troškovi i prihodi: unos, izmena, brisanje, datum, kategorija i beleška.
- Mesečni pregled i potrošnja po kategorijama; pretraga i filteri prometa.
- Mesečni budžeti po kategorijama, preostali iznos i prekoračenja.
- Jednokratni i mesečni računi, rokovi i ručno evidentiranje plaćanja.
- Paralelni iznosi RSD/EUR po poslednjem preuzetom zvaničnom srednjem kursu NBS.
- JSON rezervna kopija i vraćanje sa potvrdom; izvoz svih unosa u CSV.
- Sistemska, svetla i tamna tema.

## Offline i kurs

Unosi, budžeti i računi čuvaju se u SQLite bazi na telefonu. Nema naloga, servera, reklama, analitike niti slanja finansijskih podataka.

Jedini mrežni zahtev je HTTPS GET ka [zvaničnoj srednjoj kursnoj listi NBS](https://webappcenter.nbs.rs/ExchangeRateWebApp/ExchangeRate/CurrentMiddleRate). Kurs se proverava pri otvaranju/povratku u aplikaciju, najviše jednom na sat, ili ručno dugmetom za osvežavanje. Datum prikazan u aplikaciji je datum NBS liste, a ne datum preuzimanja. Vikendom i praznicima primenjuje se poslednja objavljena lista. Kada nema mreže ili se promeni format stranice NBS, ostaje sačuvani kurs uz status nedostupnosti.

Prvo pokretanje ima provereni početni snimak NBS liste od 11.09.2026. (117,3580 RSD za EUR), označen datumom. Uspešno preuzimanje ga zamenjuje novijom listom. EUR prikaz svih iznosa, uključujući ranije unose, informativan je preračun po tom prikazanom kursu; nije istorijski kurs dana transakcije ni kurs menjačnice. Osnovni iznosi čuvaju se u parama kao `Long`, bez grešaka binarnog decimalnog računanja.

Mesečni račun nije automatski trošak: potvrda plaćanja upisuje stvarni iznos i datum, pa pomera sledeći rok u jednoj transakciji baze. Aplikacija ne šalje sistemske podsetnike. Brisanje aplikacije briše lokalne podatke. Pre toga izvezi rezervnu kopiju. Kopija nije šifrovana; čuvaj je na bezbednom mestu. Automatski Android cloud backup je isključen.

## Jednostavna struktura

Jedan modul `app`, Kotlin + Jetpack Compose + SQLiteOpenHelper. Nema DI okvira, servera ni višeslojne infrastrukture.

| Fajl | Odgovornost |
| --- | --- |
| `MainActivity.kt` | Ekrani i forme |
| `AppModel.kt` | Stanje, pozadinske operacije, uvoz/izvoz |
| `Models.kt` | Modeli, novac, datumi |
| `Database.kt` | SQLite i atomsko plaćanje računa |
| `Backup.kt` | Validacija JSON kopije i CSV |
| `ExchangeRate.kt` | NBS preuzimanje, provera liste i preračun |

## Izgradnja

JDK 17+, Android SDK 35, Gradle 8.13. Android Studio može otvoriti ovaj direktorijum.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest
```

GitHub Actions gradi APK i pokreće provere. Debug APK iz Actions služi testiranju; za instalaciju koristi potpisani APK iz Releases. Release ključ nije deo repozitorijuma. Nepotpisani release iz CI-ja mora biti potpisan istim privatnim ključem pre objavljivanja naredne verzije, kako bi nadogradnja sačuvala podatke.

## Inspiracija

Pregled kategorija i budžeta oslanja se na uobičajene obrasce opisane u [YNAB](https://www.ynab.com/features), a tok predstojećih računa na [Wallet / BudgetBakers](https://budgetbakers.com/en/products/wallet/features/planned-payments/). Kod, izgled i naziv aplikacije su nezavisni.
