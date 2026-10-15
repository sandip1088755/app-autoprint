# AutoPrint Android app (GitHub se APK)

Ye project aapki website (customer panel) ko ek asli Android app mein badalta hai.
Ek hi app sabhi shops ke liye: app `app.php` (AutoPrint home) kholti hai, jahan se
customer shop chunta hai. Photo/PDF upload, camera, UPI apps, call/WhatsApp sab chalte hain.

APK GitHub khud banata hai (free). Aapko apne computer par kuch install nahi karna.

## 1) Link set karo (sirf ek baar)
`gradle.properties` mein ye line apne `app.php` ke link se badlo:

    START_URL=https://autoprint.freedev.app/s/app.php?src=app

(Ya GitHub mein: repo -> Settings -> Secrets and variables -> Actions -> **Variables** ->
New variable, naam `START_URL`, value apna link. Variable ho to wahi chalta hai.)

## 2) GitHub par daalo
1. github.com par login -> **New repository** (naam `autoprint-app`, Private rakh sakte ho).
2. Is zip ko computer par extract karo. Repo mein **Add file -> Upload files** dabao aur
   extract kiye folder ke andar ki **saari files aur folders** drag-and-drop karo -> **Commit changes**.
3. Dhyan do: `.github/workflows/build-apk.yml` file bhi chali jani chahiye. Agar upload
   mein wo na dikhe (chhupa folder), to repo mein **Add file -> Create new file** dabao,
   naam likho `.github/workflows/build-apk.yml` aur is zip wali file ka poora text paste karo.

## 3) APK nikalo
1. Repo ke upar **Actions** tab -> "Build Android APK" -> (khud chalega, ya **Run workflow**).
2. 4-8 minute mein hara tick aayega. Us run par click karo, neeche **Artifacts** mein
   **AutoPrint-APK** download karo (zip). Andar `app-debug.apk` hai.
3. APK phone mein bhejo aur install karo ("Unknown sources" allow karna padega).

## Play Store ke liye (baad mein)
Signed `.aab` chahiye. Keystore banao (computer par, ek baar):

    keytool -genkeypair -v -keystore release.jks -alias autoprint -keyalg RSA -keysize 2048 -validity 10000

Phir repo -> Settings -> Secrets and variables -> Actions -> **Secrets** mein ye 4 daalo:
- `KEYSTORE_BASE64`  = release.jks ka base64  (`base64 -w0 release.jks`)
- `KEYSTORE_PASSWORD`, `KEY_ALIAS` (autoprint), `KEY_PASSWORD`

Ab har build mein signed `.apk` aur Play Store ka `.aab` bhi milega.
**release.jks ko sambhal kar rakho. Kho gayi to app update nahi hoga.**

## Naam / package badalna
- App ka naam: `app/src/main/res/values/strings.xml`
- Package ID (`app.freedev.autoprint`): `app/build.gradle` mein `namespace` aur `applicationId`,
  aur `app/src/main/java/app/freedev/autoprint/` folder ka naam.

## Agar build fail ho
Actions mein laal run kholo, jis step par laal x ho wo khol kar **aakhri 30 lines** copy karke bhej do.

## Jo is app mein NAHI hai
- "Print ready" push notification (iske liye Firebase alag se lagana padega).
- Razorpay card/netbanking WebView mein chalta hai; UPI apps seedha khulte hain.
- Chrome ke saath "Add to Home screen" wala install card yahan nahi dikhta (app khud install hai).
