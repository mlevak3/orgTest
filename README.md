# Fiskal Obrt — mobilna fiskalizacija B2C računa

Mala Android aplikacija (APK) za fiskalizaciju računa u krajnjoj potrošnji (B2C)
prema **Tehničkoj specifikaciji za korisnike, Fiskalizacija v2.6** (Porezna
uprava / Fiskalizacija 1.0). Upisuješ zaglavlje i stavke računa, aplikacija
lokalno izračuna **ZKI**, potpiše zahtjev i pošalje ga na CIS te vrati **JIR**.

Podržani načini plaćanja: **G** (gotovina), **K** (kartica), **T** (transakcijski
račun), **C** (ček), **O** (ostalo).

> **Napomena (2026.):** od 1.1.2026. fiskaliziraju se **svi** računi izdani
> potrošačima (B2C), bez obzira na način plaćanja — ne samo gotovinski.

---

## Što aplikacija radi

1. **ZKI (Zaštitni kôd izdavatelja)** — računa se lokalno:
   `MD5( RSA‑SHA1( OIB + DatVrijeme + BrOznRac + OznPosPr + OznNapUr + IznosUkupno ) )`
   → 32 heksadecimalna znaka.
2. **RacunZahtjev** — gradi se u kanonskom (Exclusive C14N) obliku.
3. **XML potpis (XML‑DSig, enveloped)** — `rsa-sha1`, `sha1`, `exc-c14n#`,
   `KeyInfo` s X.509 certifikatom i izdavateljem/serijskim brojem.
4. **Slanje na CIS** preko SOAP/HTTPS (port 8449) i parsiranje **JIR**‑a ili greške.

Okoline:

| Okolina    | URL                                                          | Certifikat            |
|------------|--------------------------------------------------------------|-----------------------|
| TEST       | `https://cistest.apis-it.hr:8449/FiskalizacijaServiceTest`   | FINA **DEMO**         |
| PRODUKCIJA | `https://cis.porezna-uprava.hr:8449/FiskalizacijaService`    | FINA aplikacijski     |

---

## Preduvjeti (prije korištenja)

1. **FINA aplikacijski certifikat** (`.p12` / `.pfx`) s privatnim ključem.
   - Za testiranje koristi **FINA DEMO** certifikat.
   - Za produkciju koristi pravi aplikacijski (produkcijski) certifikat.
2. **Prijavljen poslovni prostor i naplatni uređaj** (preko ePorezne / Porezne
   uprave). Ova aplikacija **ne** radi prijavu poslovnog prostora — samo
   fiskalizira račune. Oznake `OznPosPr` i `OznNapUr` moraju odgovarati
   prijavljenima.
3. **OIB obveznika** (i operatera, ako je različit).

---

## Kako dobiti APK (GitHub Actions)

Build se vrti automatski na svaki push (workflow `.github/workflows/android.yml`):

1. Otvori karticu **Actions** na GitHubu → zadnji uspješni **„Build APK"** run.
2. U **Artifacts** preuzmi `fiskal-obrt-debug-apk` (ZIP) i raspakiraj
   `app-debug.apk`.
3. Workflow možeš pokrenuti i ručno (**Run workflow**).

Lokalni build (alternativa, treba Android SDK + Gradle 8.14.3 ili Android Studio):

```bash
gradle assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
gradle test               # pokreće testove fiskalizacijske jezgre
```

> Ako koristiš Gradle wrapper, prvo ga generiraj: `gradle wrapper --gradle-version 8.14.3`.
> Android Studio ga generira automatski pri otvaranju projekta.

---

## Instalacija na telefon

1. Prebaci `app-debug.apk` na Android uređaj.
2. Dopusti **instalaciju iz nepoznatih izvora** za preglednik/datoteke.
3. Otvori APK i instaliraj. (Ovo je *debug* APK, dovoljan za osobnu upotrebu.)

---

## Korištenje

1. **Postavke** (gore desno) → unesi OIB, oznaku poslovnog prostora i naplatnog
   uređaja, oznaku slijednosti, te označi jesi li u sustavu PDV‑a.
2. **Učitaj certifikat** (`.p12`) i upiši **lozinku** certifikata. Spremanje
   pokušava validirati certifikat i javi je li lozinka ispravna.
3. Odaberi **okolinu** (TEST za probu). Za TEST poslužitelj po potrebi uključi
   *„Zanemari TLS provjeru"* (demo poslužitelj koristi FINA DEMO CA koji nije u
   Android trust storeu). **Za produkciju NIKAD ne uključuj** tu opciju.
4. Na ekranu **računa** dodaj stavke (naziv, količina, cijena; PDV % ako si u
   sustavu PDV‑a) i odaberi način plaćanja.
5. **FISKALIZIRAJ** → dobiješ **JIR** i **ZKI**. Broj računa se automatski
   poveća tek kad CIS prihvati račun.

Ako slanje ne uspije (npr. nema mreže), **ZKI je svejedno izračunat** i mora se
otisnuti na računu (postupak naknadne dostave JIR‑a u roku propisanom zakonom).

---

## Provjera ispravnosti (testovi)

`app/src/test/.../FiskalCoreTest.kt` provjerava jezgru na JVM‑u:

- ZKI je 32 hex znaka i determinističan; podudara se s nezavisnim izračunom.
- `RacunZahtjev` je valjan XML s ključnim poljima.
- **XML potpis se validira standardnim JDK XML‑DSig validatorom** (potvrda da su
  Exclusive C14N, SHA‑1 sažetak i RSA‑SHA1 potpis točni).
- `DigestValue` odgovara SHA‑1 sažetku kanonskog `RacunZahtjev`‑a.

Pokretanje: `./gradlew test` (radi i u CI‑ju prije builda APK‑a).

> **Preporuka prije produkcije:** fiskaliziraj nekoliko računa na **TEST**
> okolini s FINA **DEMO** certifikatom i potvrdi da CIS vraća JIR. Time se
> potvrđuje cijeli lanac (certifikat, potpis, mreža) s tvojim stvarnim podacima.

---

## Sigurnost

- Lozinka certifikata i konfiguracija čuvaju se u **EncryptedSharedPreferences**.
- `.p12` se sprema u **privatnu internu pohranu** aplikacije.
- Certifikati (`*.p12`, `*.pfx`, `*.jks`) su u `.gitignore` — **ne commitati ih**.
- Opcija zaobilaženja TLS provjere dostupna je **isključivo za TEST** okolinu.

---

## Tehnički detalji

- Native Android (Kotlin + Jetpack Compose), `minSdk 26`, `targetSdk 34`.
- Kriptografija preko `java.security` (PKCS#12, `SHA1withRSA`, `MD5`, `SHA-1`).
- Transport: OkHttp (SOAP/HTTPS).
- Format datuma/vremena: `dd.MM.yyyy'T'HH:mm:ss`. Iznosi: točka, 2 decimale.
- Nije uključeno: prijava poslovnog prostora (`PoslovniProstorZahtjev`),
  storno/naknadna dostava UI — po potrebi se dograđuje.
