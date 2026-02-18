# 📱 Mobilna aplikacija – E2 2024/2025
👥 **Tim 43**

---

## 📖 Uvod

Ovo je mobilna Android aplikacija za upravljanje zadacima sa integrisanim sistemom gamifikacije.
Aplikacija kombinuje organizaciju dnevnih obaveza sa sistemom nagrađivanja, napredovanja kroz nivoe i borbe protiv Boss protivnika.

Cilj projekta je implementacija funkcionalne mobilne aplikacije sa jasno definisanom arhitekturom, Firebase backend integracijom i modularnom organizacijom koda.

---

## 🧩 Opis sistema

Aplikacija omogućava korisnicima da:

- Kreiraju i organizuju zadatke
- Prate napredak kroz XP i nivo sistem
- Učestvuju u Boss borbama
- Kupuju i aktiviraju opremu
- Formiraju saveze i komuniciraju sa drugim korisnicima

> Sistem koristi Firebase kao backend platformu za autentifikaciju, skladištenje podataka i notifikacije.

---

## 🏗️ Arhitektura aplikacije

Projekat je organizovan slojevito, sa jasnom podelom odgovornosti.

### 📦 Model sloj
Sadrži domenske entitete koji predstavljaju osnovne podatke sistema:
- User
- Task
- Category
- Boss
- Equipment (Weapon, Clothes, Potion)
- Alliance
- ChatMessage

Model klase predstavljaju strukturu podataka koji se čuvaju u Cloud Firestore bazi.

### 🗂️ Repository sloj
Repository sloj je zadužen za komunikaciju sa Firebase backend-om. Implementirani repozitorijumi:
- AuthRepository
- UserRepository
- TaskRepository
- CategoryRepository
- BossRepository
- EquipmentRepository

Ovaj sloj odvaja izvor podataka od poslovne logike i UI komponenata.

### ⚙️ Service sloj
Service sloj implementira poslovnu logiku aplikacije i pozadinske procese. Ključne komponente:
- **AllianceService** (Background Service za sinhronizaciju)
- **NotificationReceiver** (Obrada notifikacija)
- TaskService
- BossService
- LevelingService

Ovaj sloj obrađuje podatke dobijene iz repository sloja i priprema ih za prikaz u UI sloju.

### 🖥️ UI sloj
Korisnički interfejs je implementiran korišćenjem Activity + Fragment pristupa uz Material Design. Glavne funkcionalne celine:
- **Auth:** Login, Register, VerifyMail
- **Profile:** ChangePassword, LevelProgress
- **Boss:** BossPrepActivity, BossFightActivity, FightResultActivity
- **Equipment:** EquipmentStoreActivity, MyEquipmentActivity

Navigacija je realizovana pomoću Android Navigation Component-a.

---

## 🚀 Funkcionalnosti

### 🔐 Autentifikacija
- Registracija korisnika putem email adrese i lozinke
- Verifikacija email adrese
- Prijava i odjava
- Promena lozinke

Autentifikacija je realizovana korišćenjem Firebase Authentication servisa.

### 📋 Upravljanje zadacima
Korisnik može:
- Kreirati jednokratne i ponavljajuće zadatke
- Dodeliti kategoriju zadatku (uz odabir boja pomoću AmbilWarna)
- Pregledati i filtrirati zadatke
- Označiti zadatak kao završen

Nakon završetka zadatka korisnik dobija XP poene i Novčiće. Podaci o zadacima čuvaju se u Cloud Firestore bazi.

### 📈 Sistem napredovanja
Aplikacija implementira XP sistem i sistem nivoa. Karakteristike:
- Prikupljanje XP poena
- Automatsko povećanje nivoa (prikazano kroz LevelProgressActivity)
- Prikaz napretka korisnika i statistike (MPAndroidChart)
- Titule koje se otključavaju kroz napredovanje

### ⚔️ Boss sistem
Boss sistem predstavlja centralni gamifikacioni element aplikacije. Borba se sastoji od:
- Pripreme borbe (izbor opreme)
- Izvršavanja borbe (korišćenje senzora i vibracije)
- Prikaza rezultata

Težina Boss protivnika skalira u skladu sa nivoom korisnika.

### 🛒 Sistem opreme
Korisnik može kupovati i aktivirati opremu, kao i koristiti opremu tokom Boss borbe.
Tipovi opreme:
- Weapon
- Clothes
- Potion

Oprema utiče na performanse u borbi.

### 🛡️ Savez i socijalne funkcionalnosti
Aplikacija omogućava:
- Kreiranje saveza
- Dodavanje članova (QR skener - ZXing)
- Slanje poruka unutar saveza
- Pozadinska sinhronizacija podataka

Podaci o savezima i komunikaciji čuvaju se u Firestore bazi.

---

## ⚙️ Tehnologije

Tehnologije korišćene u projektu:

- **Platforma:** Android (minSdk 30, targetSdk 36)
- **Programski jezik:** Java 11
- **Backend:** Firebase Authentication, Cloud Firestore, Firebase Cloud Messaging
- **UI & Grafika:** Material Components, Lottie Animations (v6.4.1), MPAndroidChart (v3.1.0)
- **Ostalo:** ZXing Android Embedded (QR), AmbilWarna (Color Picker)

---

## ▶️ Pokretanje projekta

1. Klonirati repozitorijum.
2. Dodati `google-services.json` fajl u `app/` direktorijum.
3. Konfigurisati Firebase projekat (Authentication, Firestore, Messaging).
4. Otvoriti projekat u Android Studio okruženju.
5. Pokrenuti aplikaciju na emulatoru ili fizičkom Android uređaju (preporučeno zbog senzora i vibracije).

---

## 👨‍💻 Autori

**Miloslav Đenđur** (RA 26/2021)
**Relja Jocić** (RA 192/2021)

---