<div align="center">

<a href="https://github.com/technicalnihilist-lgtm/pehchan-ai/stargazers"><img src="https://img.shields.io/badge/stars-7-5A31F4?style=for-the-badge&logo=github&logoColor=white" alt="stars: 7"></a>&nbsp;<a href="https://github.com/technicalnihilist-lgtm/pehchan-ai/network/members"><img src="https://img.shields.io/badge/forks-0-3FB950?style=for-the-badge" alt="forks: 0"></a>

<h3>Pehchan AI - offline on-device image identifier for Android</h3>

<img src="https://img.shields.io/badge/Java-5865F2?style=flat-square" alt="Java"> <img src="https://img.shields.io/badge/Ai-3B3F4A?style=flat-square" alt="Ai"> <img src="https://img.shields.io/badge/Android-3FB950?style=flat-square" alt="Android"> <img src="https://img.shields.io/badge/APK-EC4899?style=flat-square" alt="APK">

<a href="https://github.com/technicalnihilist-lgtm/pehchan-ai/releases/latest"><img src="https://img.shields.io/badge/DOWNLOAD-APK-6D5AE6?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"></a>

<b>Support the work</b>

<img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-COMING%20SOON-4B5563?style=for-the-badge&logo=buymeacoffee&logoColor=white" alt="Buy me a coffee — coming soon"> <img src="https://img.shields.io/badge/UPI-COMING%20SOON-4B5563?style=for-the-badge&logo=googlepay&logoColor=white" alt="UPI — coming soon"> <img src="https://img.shields.io/badge/PayPal-COMING%20SOON-4B5563?style=for-the-badge&logo=paypal&logoColor=white" alt="PayPal — coming soon">

<sub>Donations aren't live yet — Paytm, UPI, PayPal and Buy&nbsp;Me&nbsp;a&nbsp;Coffee all show <b>coming soon</b>. Until then, a ⭐ star is the best support.</sub>

<a href="https://github.com/technicalnihilist-lgtm/pehchan-ai/releases/latest"><img src="https://img.shields.io/badge/VERSION-v1.6-6D5AE6?style=for-the-badge" alt="VERSION: v1.6"></a> <img src="https://img.shields.io/badge/DOWNLOADS-738-3FB950?style=for-the-badge" alt="DOWNLOADS: 738"> <a href="https://github.com/technicalnihilist-lgtm/pehchan-ai/releases"><img src="https://img.shields.io/badge/RELEASES-GITHUB-EC4899?style=for-the-badge&logo=github&logoColor=white" alt="RELEASES: GITHUB"></a>

<h4>Get it from</h4>

<a href="https://github.com/technicalnihilist-lgtm/pehchan-ai/releases"><img src="https://img.shields.io/badge/GitHub-Releases-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Releases"></a> <a href="https://github.com/technicalnihilist-lgtm/pehchan-ai/archive/refs/heads/main.zip"><img src="https://img.shields.io/badge/Source-ZIP-0EA5E9?style=for-the-badge&logo=github&logoColor=white" alt="Source ZIP"></a>

</div>

---

---

## Download & install

**[⬇ Download PehchanAI-v1.6.apk (14 MB)](https://github.com/technicalnihilist-lgtm/pehchan-ai/releases/download/v1.6/PehchanAI-v1.6.apk)**

1. Tap the link on your phone (or open the [v1.6 release](https://github.com/technicalnihilist-lgtm/pehchan-ai/releases/tag/v1.6)).
2. Android asks to allow installs from this source → allow.
3. Install, open, done. **Android 5.0 (API 21) and newer**, any phone.

```
SHA-256  01cf5fbf298b3f0dcfb68aaf3765e739920c9718e6169346a815f66401037c48
```

Signed with v1 + v2 + v3 schemes, so it installs cleanly on modern Android.
Version 1.6 installs *over* v1.0–v1.5 (same signing key).

## What it does

* **Finds every object** — an on-device detector draws a box around each thing it sees and
  lists them with confidence: people, dogs, cats, birds, food, phones, laptops, TVs, chairs,
  tables, cars, bicycles, bottles, cups, and 60+ more categories.
* **Tap any object to look closer** — the app crops just that part of the photo and runs the
  fine-detail model on it, so "dog" becomes "golden retriever" and "car" becomes "sports car".
* **Plain-language answer** — "This looks like a golden retriever." + `55% match` + a
  confidence word (High / Fairly confident / Best guess), so the number means something.
* **Image details** — real photo size, file size, lighting (Bright / Balanced / Dark) and the
  five **dominant colours** with hex codes.
* **Search on Google Lens** — one tap for anything the on-device models don't know
  (brands, monuments, rare plants, specific products).
* **Wikipedia** — opens the article for the top match.
* **Share** — sends the photo plus the result text to any app.
* **Open-with support** — share an image *to* Pehchan AI from your gallery or any other app.

## Why it feels fast and safe

| | |
|---|---|
| **100% offline** | The app declares **no internet permission at all** — it physically cannot upload your photos. |
| **No permissions** | No storage, camera or location permission. Photos come through Android's own file picker; the camera hands back one photo. |
| **~14 MB** | Both models and the runtime are bundled; nothing is downloaded later. |
| **Works on any phone** | Native libraries for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`. |

## How it works

```
photo ──► decode (max 1600 px) ──► EXIF rotation fix
      ├─► 300×300  SSD-MobileNet detector ──► up to 10 boxes + labels  ──► drawn on the photo
      └─► 224×224  EfficientNet-Lite0      ──► 1000 ImageNet scores    ──► "best match"
                                          tap an object ──► crop ──► classify the crop
```

* **Detector** — SSD MobileNet V1, fully quantised, 300×300 uint8, COCO 80 classes, with
  non-max suppression built into the graph (`app/assets/detect.tflite`, 4.0 MB).
* **Classifier** — EfficientNet-Lite0 int8, 224×224 uint8, ImageNet-1k head
  (`app/assets/model.tflite`, 5.2 MB) — used for the whole photo *and* for the crop you tap.
* **Boxes** — drawn by `OverlayView`, which repeats the preview's centre-crop transform so the
  boxes stay glued to the right pixels at any photo shape.
* **Colours / lighting** — computed on a 44 px thumbnail in `ImageInfo.java`.
* **No support library, no Kotlin, no Compose** — plain Android framework classes, so the APK
  stays small and starts instantly.

### Honest limits

The detector knows the **80 COCO categories** — person, bicycle, car, motorcycle, airplane,
bus, train, truck, boat, traffic light, fire hydrant, stop sign, parking meter, bench, bird,
cat, dog, horse, sheep, cow, elephant, bear, zebra, giraffe, backpack, umbrella, handbag, tie,
suitcase, frisbee, skis, snowboard, sports ball, kite, baseball bat, baseball glove, skateboard,
surfboard, tennis racket, bottle, wine glass, cup, fork, knife, spoon, bowl, banana, apple,
sandwich, orange, broccoli, carrot, hot dog, pizza, donut, cake, chair, couch, potted plant,
bed, dining table, toilet, tv, laptop, mouse, remote, keyboard, cell phone, microwave, oven,
toaster, sink, refrigerator, book, clock, vase, scissors, teddy bear, hair drier, toothbrush.

The classifier adds **1000 fine-grained ImageNet classes** on top (dog breeds, bird species,
instruments, tools, and so on). Anything outside both lists — a specific brand, a monument, a
rare plant, a person's name — will come back as a low-confidence near-miss, which is exactly
what the **Search on Google Lens** button is for. No on-device model recognises *every*
object in the world; this one recognises a lot of them, instantly and privately.

## Build it yourself

No Gradle — just the Android SDK build-tools, which keeps the build easy to reproduce:

```bash
## Project layout

```
app/
├── AndroidManifest.xml              no permissions, portrait, share/VIEW filters
├── build.sh                         the whole build, 8 steps
├── assets/
│   ├── detect.tflite                SSD-MobileNet detector, COCO 80 (fetched from the release)
│   ├── coco_labels.txt              the 80 object names
│   ├── model.tflite                 EfficientNet-Lite0 int8 (fetched from the release)
│   └── labels.txt                   1000 ImageNet labels
├── res/
│   ├── layout/activity_main.xml     single screen, one scroll
│   ├── drawable/                    gradients, cards, 9 hand-written vector icons
│   ├── values/                      colours, strings, theme
│   └── mipmap-*/                    launcher icon (adaptive + PNG fallbacks)
└── src/com/rajok/pehchanai/
    ├── MainActivity.java            UI, picking, camera, results, sharing
    ├── Detector.java                object detector + boxes
    ├── OverlayView.java             draws the boxes on the photo
    ├── Classifier.java              fine-detail classifier (whole photo + tapped crop)
    ├── ImageInfo.java               size / lighting / dominant colours
    └── FileShareProvider.java       tiny ContentProvider for camera + share
screenshots/                         UI previews (1080×2340)
```

## Changelog

**v1.6** — liquid-glass look. Every surface is now a translucent pane: cards and buttons use
a white 4–15% gradient fill with a 1 dp hairline border, the top-match card carries a violet
tint, pills and progress tracks are see-through, and the background has three soft colour
glows (violet, blue, teal) so the glass has something to show through. Pure drawables — no
blur dependency, so it looks the same on Android 5 through 15.

**v1.5** — smaller chrome, more photo: Gallery and Camera are 42 dp tall and Identify 46 dp
(13 sp / 15 sp text) instead of 50/56 dp, and the boxes drawn on the photo are lighter —
1.5 dp outline, much fainter fill, smaller label chips — so the image itself stays readable.

**v1.4** — slimmer UI: the confidence bars in OBJECTS FOUND and OTHER POSSIBILITIES are
4 dp instead of 7 dp with tighter row spacing, so the lists read as data instead of blocks,
and the object labels drawn on the photo are a little smaller so they cover less of the
image.

**v1.3** — fixes "Could not analyse this photo" on v1.1/v1.2. The detector's output buffer
for the detection-count tensor was allocated as `float[1][1]` while the tensor's shape is
`[1]`, so TensorFlow Lite threw a shape-mismatch error and took the whole analysis down with
it. Output buffers are now allocated from the shapes the interpreter reports. Also: the two
models fail independently (a detector problem can no longer hide the classifier result),
overlapping duplicate boxes are collapsed (a plate of food no longer lists "pizza" four
times), the object list shows one row per object type, and a long-press on the app name
shows the device details and the last error so problems can be reported exactly.

**v1.2** — the preview now shows the **whole photo**, never a cropped version: the image
fits inside the frame (`fitCenter`) and the frame takes the photo's own aspect ratio
(clamped between 180 and 470 dp), so a portrait shot is tall, a landscape shot is wide, and
nothing is cut off. Detection boxes follow the same fit-inside transform.

**v1.1** — object detection: every object in the photo is boxed and listed; tap an object to
classify just that part. New `OBJECTS FOUND` card, box overlay, richer share text.

**v1.0** — first release: single-model image identifier, image details, Lens/Wikipedia/Share.

## Credits

* Detector: SSD MobileNet V1 COCO (TensorFlow, Apache-2.0).
* Classifier: [EfficientNet-Lite0](https://github.com/tensorflow/tpu/tree/master/models/official/efficientnet/lite),
  converted and quantised by the TensorFlow team.
* Runtime: [TensorFlow Lite](https://www.tensorflow.org/lite) 2.14.0 (Apache-2.0).
* Labels: ImageNet class names, COCO category names.
* Everything else — UI, code, icons — written for this project.

## License

MIT — see [LICENSE](LICENSE).

---

---

## 🌍 Languages

<sub>Donations are <b>coming soon</b> in every language below — nothing to pay today, nothing to click.</sub>

<div align="center">

<sub><b>हिन्दी · বাংলা · मराठी · తెలుగు · தமிழ் · ગુજરાતી · ಕನ್ನಡ · മലയാളം · ਪੰਜਾਬੀ · ଓଡ଼ିଆ · অসমীয়া · اردو</b></sub>

</div>

<details>
<summary><b>🌍 Open the same message in 53 languages</b></summary>

**🇮🇳 Indian languages**

- **हिन्दी** (Hindi) — ☕ जल्द ही आ रहा है। · ⭐ अगर आपको यह ऐप और हमारा काम पसंद है, तो कृपया रिपॉजिटरी को स्टार दें और हमें फॉलो करें।
- **বাংলা** (Bengali) — ☕ খুব শিগগিরই আসছে। · ⭐ যদি আপনার এই অ্যাপ এবং আমাদের কাজ ভালো লাগে, তবে অনুগ্রহ করে রিপোজিটরিটিকে একটি স্টার দিন এবং আমাদের ফলো করুন।
- **मराठी** (Marathi) — ☕ लवकरच येत आहे. · ⭐ जर तुम्हाला हे ॲप आणि आमचं काम आवडलं असेल, तर कृपया रिपॉझिटरीला स्टार द्या आणि आम्हाला फॉलो करा.
- **తెలుగు** (Telugu) — ☕ త్వరలో రాబోతోంది. · ⭐ మీకు ఈ యాప్, మా పని నచ్చితే, దయచేసి రిపోజిటరీకి ఒక స్టార్ ఇచ్చి మమ్మల్ని ఫాలో అవ్వండి.
- **தமிழ்** (Tamil) — ☕ விரைவில் வருகிறது. · ⭐ உங்களுக்கு இந்த ஆப்பும் எங்க வேலையும் பிடிச்சிருந்தா, தயவுசெஞ்சு இந்த ரெபாசிட்டரிக்கு ஒரு ஸ்டார்…
- **ગુજરાતી** (Gujarati) — ☕ ટૂંક સમયમાં આવી રહ્યું છે. · ⭐ જો તમને આ એપ અને અમારું કામ ગમતું હોય, તો પ્લીઝ રિપોઝિટરીને સ્ટાર આપો અને અમને ફોલો કરો.
- **ಕನ್ನಡ** (Kannada) — ☕ ಶೀಘ್ರದಲ್ಲೇ ಬರುತ್ತಿದೆ. · ⭐ ನಿಮಗೆ ಈ ಆ್ಯಪ್ ಮತ್ತು ನಮ್ಮ ಕೆಲಸ ಇಷ್ಟವಾದರೆ, ದಯವಿಟ್ಟು ರೆಪೊಸಿಟರಿಗೆ ಸ್ಟಾರ್ ಕೊಡಿ ಮತ್ತು ನಮ್ಮನ್ನು ಫಾಲೋ ಮಾಡಿ.
- **മലയാളം** (Malayalam) — ☕ ഉടൻ വരുന്നു. · ⭐ നിങ്ങൾക്ക് ഈ ആപ്പും ഞങ്ങളുടെ പ്രവർത്തനവും ഇഷ്ടപ്പെട്ടെങ്കിൽ, ദയവായി റെപ്പോസിറ്ററിക്ക് ഒരു സ്റ്റാർ…
- **ਪੰਜਾਬੀ** (Punjabi) — ☕ ਜਲਦੀ ਆ ਰਿਹਾ ਹੈ। · ⭐ ਜੇ ਤੁਹਾਨੂੰ ਇਹ ਐਪ ਅਤੇ ਸਾਡਾ ਕੰਮ ਪਸੰਦ ਹੈ, ਤਾਂ ਕਿਰਪਾ ਕਰਕੇ ਰਿਪੋਜ਼ਟਰੀ ਨੂੰ ਸਟਾਰ ਦਿਓ ਅਤੇ ਸਾਨੂੰ ਫਾਲੋ ਕਰੋ।
- **ଓଡ଼ିଆ** (Odia) — ☕ ଖୁବ୍ ଶୀଘ୍ର ଆସୁଛି। · ⭐ ଯଦି ଆପଣଙ୍କୁ ଏହି ଆପ୍ ଏବଂ ଆମର କାମ ଭଲ ଲାଗୁଛି, ତେବେ ଦୟାକରି ରିପୋଜିଟୋରୀକୁ ଗୋଟିଏ ଷ୍ଟାର୍ ଦିଅନ୍ତୁ ଏବଂ ଆମକୁ ଫଲୋ କରନ୍ତୁ।
- **অসমীয়া** (Assamese) — ☕ অতি সোনকালেই আহি আছে। · ⭐ যদি আপুনি এই এপটো আৰু আমাৰ কাম ভাল পায়, তেন্তে অনুগ্ৰহ কৰি ৰিপজিটৰীটোক এটা ষ্টাৰ দিয়ক আৰু আমাক ফ'ল' কৰক।
- **اردو** (Urdu) — ☕ جلد آرہا ہے۔ · ⭐ اگر آپ کو یہ ایپ اور ہمارا کام پسند آئے تو براہ کرم ریپازٹری کو سٹار دیں اور ہمیں فالو کریں۔
- **संस्कृतम्** (Sanskrit) — ☕ शीघ्रमेव आगच्छति।
- **मैथिली** (Maithili) — ☕ जल्दिये आबि रहल अछि।
- **कोंकणी** (Konkani) — ☕ रोखडेंच येता.
- **کٲشُر** (Kashmiri) — ☕ جَلدی یِوان چھُ۔
- **سنڌي** (Sindhi) — ☕ जल्द अची रह्यो आहे।
- **डोगरी** (Dogri) — ☕ जल्दी आ करदा ऐ।
- **ꯃꯤꯇꯩ ꯂꯣꯟ** (Manipuri) — ☕ ꯑꯊꯨꯕꯥ ꯃꯇꯝꯗꯥ ꯂꯥꯛꯀꯅꯤ꯫
- **बर’** (Bodo) — ☕ थाबैनो फैगोन।
- **ᱥᱟᱱᱛᱟᱲᱤ** (Santali) — ☕ ᱞᱚᱜᱚᱱ ᱦᱤᱡᱩᱜ ᱠᱟᱱᱟ᱾
- **नेपाली** (Nepali) — ☕ छिट्टै आउँदैछ। · ⭐ यदि तपाईंलाई यो एप र हाम्रो काम मन पर्‍यो भने, कृपया रिपोजिटरीलाई स्टार दिनुहोस् र हामीलाई फलो गर्नुहोस्।

**🌍 World languages**

- **العربية** (Arabic) — ☕ قريبًا. · ⭐ إذا أعجبك هذا التطبيق وعملنا، فيرجى منح المستودع نجمة ومتابعتنا.
- **简体中文** (Chinese (Simplified)) — ☕ 即将推出。
- **繁體中文** (Chinese (Traditional)) — ☕ 即將推出。
- **Čeština** (Czech) — ☕ Již brzy.
- **Dansk** (Danish) — ☕ Kommer snart.
- **Nederlands** (Dutch) — ☕ Binnenkort beschikbaar. · ⭐ Vind je deze app en ons werk leuk?
- **Filipino** (Filipino) — ☕ Malapit na.
- **Suomi** (Finnish) — ☕ Tulossa pian.
- **Français** (French) — ☕ Bientôt disponible. · ⭐ Si cette application et notre travail vous plaisent, mettez une étoile au dépôt et suivez-nous.
- **Deutsch** (German) — ☕ Bald verfügbar. · ⭐ Wenn dir diese App und unsere Arbeit gefallen, gib dem Repository einen Stern und folge uns.
- **Ελληνικά** (Greek) — ☕ Έρχεται σύντομα.
- **עברית** (Hebrew) — ☕ בקרוב.
- **Magyar** (Hungarian) — ☕ Hamarosan.
- **Bahasa Indonesia** (Indonesian) — ☕ Segera hadir. · ⭐ Jika kamu suka aplikasi ini dan karya kami, beri bintang pada repositori ini dan ikuti kami.
- **Italiano** (Italian) — ☕ In arrivo.
- **日本語** (Japanese) — ☕ 近日公開。 · ⭐ このアプリと私たちの仕事が気に入ったら、リポジトリにスターを付けてフォローしてください。
- **한국어** (Korean) — ☕ 곧 공개됩니다. · ⭐ 이 앱과 저희 작업이 마음에 드신다면 저장소에 스타를 눌러 주시고 팔로우해 주세요.
- **Bahasa Melayu** (Malay) — ☕ Akan datang.
- **Norsk** (Norwegian) — ☕ Kommer snart.
- **فارسی** (Persian) — ☕ به‌زودی.
- **Polski** (Polish) — ☕ Wkrótce. · ⭐ Jeśli podoba Ci się ta aplikacja i nasza praca, daj repozytorium gwiazdkę i obserwuj nas.
- **Português** (Portuguese) — ☕ Em breve.
- **Română** (Romanian) — ☕ În curând.
- **Русский** (Russian) — ☕ Скоро. · ⭐ Если вам нравится это приложение и наша работа, поставьте звёздочку репозиторию и подпишитесь на нас.
- **Español** (Spanish) — ☕ Muy pronto. · ⭐ Si te gusta esta app y nuestro trabajo, dale una estrella al repositorio y síguenos.
- **Kiswahili** (Swahili) — ☕ Inakuja hivi karibuni.
- **Svenska** (Swedish) — ☕ Kommer snart.
- **ไทย** (Thai) — ☕ เร็ว ๆ นี้
- **Türkçe** (Turkish) — ☕ Çok yakında.
- **Українська** (Ukrainian) — ☕ Незабаром.
- **Tiếng Việt** (Vietnamese) — ☕ Sắp ra mắt. · ⭐ Nếu bạn thích ứng dụng này và công việc của chúng tôi, hãy tặng kho lưu trữ một ngôi sao và theo…

</details>

