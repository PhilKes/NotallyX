<h2 align="center">
    <img src="fastlane/metadata/android/en-US/images/icon.png" alt="icon" width="90"/>
    <br />
    <b><a href="https://philkes.github.io/NotallyX/">NotallyX | Minimalistic note taking app</a></b>
    <p>
        <center>
            <a href="https://ko-fi.com/philkes"><img alt='Donate' height='30' src='documentation/static/img/kofi_donate.svg' /></a>
        </center>
    </p>
    <p>
        <center>
            <a href='https://play.google.com/store/apps/details?id=com.philkes.notallyx&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height='80'/></a>
            <a href="https://f-droid.org/en/packages/com.philkes.notallyx"><img alt='IzzyOnDroid' height='80' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' /></a>
            <a href="https://apt.izzysoft.de/fdroid/index/apk/com.philkes.notallyx"><img alt='F-Droid' height='80' src='https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png' /></a>
        </center>
    </p>
</h2>

<div style="display: flex; justify-content: space-between; width: 100%;">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Image 6" style="width: 32%;"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Image 2" style="width: 32%;"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Image 3" style="width: 32%;"/>
</div>

<div style="display: flex; justify-content: space-between; width: 100%;">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Image 4" style="width: 32%;"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Image 5" style="width: 32%;"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" alt="Image 7" style="width: 32%;"/>
</div>


### Features
[Notally](https://github.com/OmGodse/Notally), but eXtended

<h4><a href="https://philkes.github.io/NotallyX/">See Documentation</a></h4>

* Create **rich text** notes with support for bold, italics, mono space and strike-through
* Create **task lists** and order them with subtasks (+ auto-sort checked items to the end)
* Set **reminders** with notifications for important notes
* Complement your notes with any type of file such as **pictures**, PDFs, etc.
* **Sort notes** by title, last modified date, creation date
* **Color, pin and label** your notes for quick organisation
* Add **clickable links** to notes with support for phone numbers, email addresses and web urls
* **Undo/Redo actions**
* Use **Home Screen Widget** to access important notes fast
* **Lock your notes via Biometric/PIN**
* Configurable **auto-backups**
* Create quick audio notes
* Display the notes either in a **List or Grid**
* Quickly share notes by text
* Extensive preferences to adjust views to your liking
* Actions to quickly remove checked tasks
* Adaptive android app icon
* Support for Lollipop devices and up

---

### Bug Reports / Feature-Requests
If you find any bugs or want to propose a new Feature/Enhancement, feel free to [create a new Issue](https://github.com/PhilKes/NotallyX/issues/new/choose)

When using the app and an unknown error occurs, causing the app to crash you will see a dialog (see showcase video in https://github.com/PhilKes/NotallyX/pull/171) from which you can immediately create a bug report on Github with the crash details pre-filled.


#### Beta Releases

I occasionally release BETA versions of the app during development, since its very valuable for me to get feedback before publicly releasing a new version.
These BETA releases have another `applicationId` as the release versions, thats why when you install a BETA version it will show up on your device as a separate app called `NotallyX BETA`.
BETA versions also have their own data, they do not use the data of your NotallyX app
You can download the most recent BETA release [here on Github](https://github.com/PhilKes/NotallyX/releases/tag/beta)

#### APK Signing Certifcate Fingerprint

If you wish to verify your downloaded .apk file, here is the app's certificate SHA256 fingerpint:
`D2:14:B6:05:7B:79:F8:25:09:DD:CD:1E:35:19:65:B3:C6:EC:C4:B2:A3:89:6E:5C:DF:88:5A:70:A0:B6:1D:FD`

### Translations

All translations are crowd sourced.
For details on how to contribute translations and what languages are available, see [TRANSLATIONS](./TRANSLATIONS.md)

### Contributing

If you would like to contribute code yourself, just grab any open issue (that has no other developer assigned yet), leave a comment that you want to work on it and start developing by forking this repo.

The project is a default Android project written in Kotlin, I highly recommend using Android Studio for development. Also be sure to test your changes with an Android device/emulator that uses the same Android SDK Version as defined in the `build.gradle` `targetSdk`.

Before submitting your proposed changes as a Pull-Request, make sure all tests are still working (`./gradlew test`), and run `./gradlew ktfmtFormat` for common formatting (also executed automatically as pre-commit hook).

### Attribution
The original Notally project was developed by [OmGodse](https://github.com/OmGodse) under the [GPL 3.0 License](https://github.com/OmGodse/Notally/blob/master/LICENSE.md).

In accordance to GPL 3.0, this project is licensed under the same [GPL 3.0 License](https://github.com/PhilKes/NotallyX/blob/master/LICENSE.md).


