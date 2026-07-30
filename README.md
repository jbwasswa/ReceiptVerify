# Target Receipt Verifier

Small Android app for verifying Target Finance receipts.

The app accepts:

- PTID
- Verification Code

If a receipt has a QR code, the app can scan it, validate that it points to the
Target Finance verification URL, and fill the PTID and verification code
automatically.

Then loads:

```text
https://targetfinance.co.ug/verify.php?ptid=PTID&vc=CODE
```

## Build

Open this folder in Android Studio:

```text
C:\Users\Jose\Documents\DashboardsXP - KCP_00001\TargetReceiptVerifier
```

Then use **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

The APK will be created under:

```text
app\build\outputs\apk\debug\app-debug.apk
```
