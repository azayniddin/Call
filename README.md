# 📞 Zayniddin AI Call Assistant — Professional Sun'iy Intellekt Telefon Yordamchisi

Ushbu dastur **Zayniddin** uchun maxsus yaratilgan professional telefon yordamchisi (AI kotib).
Telefoningizdagi **2 ta SIM karta (Dual SIM)** bilan ishlaydi, **OpenAI (GPT-4o + TTS)** orqali tabiiy o'zbekcha ovoz hosil qiladi va qo'ng'iroqlarga avtomatik javob berib, xabarni yetkazib, aloqani avtomatik uzadi.

---

## 🌟 Asosiy Imkoniyatlar

1. **Yordamchi xizmatini yoqish / o'chirish (ON/OFF)**:
   - Ish vaqtingizda yoki band bo'lganingizda bir marta bosish orqali xizmatni yoqib qo'yasiz.
2. **Dual SIM (2 ta SIM karta) boshqaruvi**:
   - **SIM 1** (Asosiy)
   - **SIM 2** (Ish / Qo'shimcha)
   - **Ikkala SIM** (ikkala raqamga ham birdek qo'llaniladi)
3. **Erkin xabar matni (OpenAI bilan)**:
   - Istalgan xabaringizni yozib qoldirishingiz mumkin (masalan: *"men ishdaman ishdan chiqib qongiroq qilaman"* yoki *"ruldaman"*).
   - Sun'iy intellekt (GPT-4o) bu xabarni o'ta xushmuomala, rasmiy kotib nutqiga aylantiradi:
     > *"Assalomu alaykum! Men Zayniddinning sun'iy intellekt yordamchisiman. Zayniddin hozir ishda, ishdan chiqib o'zlari sizga qo'ng'iroq qiladilar. Xayr, salomat bo'ling!"*
4. **OpenAI TTS Tabiiy Insoniy Ovoz**:
   - OpenAI `tts-1` modeli orqali tiniq, insoniy ovozda audio (.mp3) generatsiya qilinadi.
5. **Avtomatik ko'tarish va o'chirish**:
   - Qo'ng'iroq kelganda telefon avtomatik ko'tariladi.
   - AI ovozi o'qib beriladi.
   - Nutq yakunlangach, telefon avtomatik o'chiriladi.
6. **Interaktiv Call Simulator**:
   - Veb-ilovaning o'zida "Qo'ng'iroqni Sinab Ko'rish" tugmasini bosib, kiruvchi qo'ng'iroq qanday kelishi, AI qanday gapirishi va trubkani qo'yishini jonli ko'rish mumkin.

---

## 🚀 Vercel'ga Deploy Qilish (1 daqiqa)

Dastur **Next.js 14** da Vercel platformasi uchun to'liq moslab tayyorlangan:

1. [Vercel.com](https://vercel.com) ga kiring.
2. **Add New... -> Project** tugmasini bosing.
3. GitHub repozitoriyangizni ulang: `https://github.com/azayniddin/Call.git`
4. **Environment Variables** bo'limiga quyidagini kiriting:
   - `OPENAI_API_KEY` = `sizning_openai_api_kalitingiz`
5. **Deploy** tugmasini bosing.

Vercel ilovangizga tayyor havola (masalan: `https://call-assistant.vercel.app`) beradi.

### 📱 Telefonga ilova sifatida o'rnatish (PWA):
1. Telefoningiz (Chrome yoki Safari) orqali Vercel bergan saytingizga kiring.
2. Brauzer menyusidan **"Bosh ekranga qo'shish" (Add to Home screen)** ni bosing.
3. Telefoningiz ish stolida xuddi haqiqiy mobil ilova kabi belgisi paydo bo'ladi!

---

## 🤖 Android Native Moduli (`android/` papkasida)

Telefondagi jismoniy SIM 1 va SIM 2 qo'ng'iroqlarini to'liq avtonom boshqarish uchun:
- `android/app/src/main/java/com/example/callguardian/service/AiCallAnswerService.kt`: `InCallService` orqali SIM kartaga qo'ng'iroq tushganda avtomatik javob berish (`call.answer()`), OpenAI audiosini suhbatdoshga eshittirish va gap tugashi bilan uzish (`call.disconnect()`).
- `android/app/src/main/java/com/example/callguardian/service/VolumeKeyAccessibilityService.kt`: Kiruvchi qo'ng'iroq paytida ovoz pasaytirish (Volume Down) tugmasi bosilganda uzish va bloklash.

---

## 🛠 Texnologiyalar
- **Frontend / Fullstack**: Next.js 14, React 18, Tailwind CSS, Lucide Icons
- **AI Backend**: OpenAI API (GPT-4o-mini + TTS-1 Voice Engine)
- **Deployment**: Vercel Serverless Functions, PWA (Progressive Web App)
- **Mobile Native**: Kotlin, Android Telecom & InCallService, Dual-SIM SubscriptionManager
