package com.clash.app

private val zapupiTranslations = mapOf(
    "en" to mapOf(
        "pay" to "PAY WITH ZAPUPI", "creating" to "CREATING PAYMENT...", "manual" to "USE MANUAL UPI INSTEAD",
        "verified_note" to "ZapUPI payments are verified by the server before wallet credit.", "complete" to "Complete ZapUPI payment",
        "secure_info" to "Complete the payment on the secure ZapUPI page. Your wallet is credited only after server verification.",
        "open" to "OPEN SECURE PAYMENT PAGE", "checking" to "CHECKING...", "check" to "CHECK PAYMENT STATUS", "close" to "CLOSE",
        "success" to "Payment verified. Wallet updated.", "failed" to "Payment was not completed. No wallet credit was added.",
        "review" to "Payment needs manual review. Contact support if you paid.", "pending" to "Payment is still pending. Check again shortly.",
        "open_error" to "Could not open the payment page", "start_error" to "Could not start payment. Try again.", "status_error" to "Could not check payment yet"
    ),
    "hi" to mapOf(
        "pay" to "ZAPUPI से भुगतान करें", "creating" to "भुगतान बनाया जा रहा है...", "manual" to "इसके बजाय मैनुअल UPI इस्तेमाल करें",
        "verified_note" to "वॉलेट में पैसे जोड़ने से पहले ZapUPI भुगतान सर्वर से सत्यापित होगा।", "complete" to "ZapUPI भुगतान पूरा करें",
        "secure_info" to "सुरक्षित ZapUPI पेज पर भुगतान पूरा करें। सर्वर सत्यापन के बाद ही वॉलेट में राशि जुड़ेगी।",
        "open" to "सुरक्षित भुगतान पेज खोलें", "checking" to "जाँच हो रही है...", "check" to "भुगतान की स्थिति जाँचें", "close" to "बंद करें",
        "success" to "भुगतान सत्यापित। वॉलेट अपडेट हो गया।", "failed" to "भुगतान पूरा नहीं हुआ। वॉलेट में राशि नहीं जोड़ी गई।",
        "review" to "भुगतान की मैन्युअल जाँच आवश्यक है। भुगतान किया हो तो सहायता से संपर्क करें।", "pending" to "भुगतान लंबित है। थोड़ी देर बाद फिर जाँचें।",
        "open_error" to "भुगतान पेज नहीं खुला", "start_error" to "भुगतान शुरू नहीं हुआ। फिर कोशिश करें।", "status_error" to "अभी भुगतान की स्थिति जाँच नहीं सके"
    ),
    "bn" to mapOf(
        "pay" to "ZAPUPI দিয়ে পেমেন্ট করুন", "creating" to "পেমেন্ট তৈরি হচ্ছে...", "manual" to "এর বদলে ম্যানুয়াল UPI ব্যবহার করুন",
        "verified_note" to "ওয়ালেটে টাকা যোগ করার আগে সার্ভার ZapUPI পেমেন্ট যাচাই করবে।", "complete" to "ZapUPI পেমেন্ট সম্পূর্ণ করুন",
        "secure_info" to "নিরাপদ ZapUPI পেজে পেমেন্ট সম্পূর্ণ করুন। সার্ভার যাচাইয়ের পরই ওয়ালেটে টাকা যোগ হবে।",
        "open" to "নিরাপদ পেমেন্ট পেজ খুলুন", "checking" to "যাচাই হচ্ছে...", "check" to "পেমেন্টের অবস্থা যাচাই করুন", "close" to "বন্ধ করুন",
        "success" to "পেমেন্ট যাচাই হয়েছে। ওয়ালেট আপডেট হয়েছে।", "failed" to "পেমেন্ট সম্পূর্ণ হয়নি। ওয়ালেটে টাকা যোগ হয়নি।",
        "review" to "পেমেন্টটি ম্যানুয়ালি যাচাই করতে হবে। পেমেন্ট করলে সহায়তায় যোগাযোগ করুন।", "pending" to "পেমেন্ট এখনও অপেক্ষমাণ। কিছুক্ষণ পরে আবার দেখুন।",
        "open_error" to "পেমেন্ট পেজ খোলা যায়নি", "start_error" to "পেমেন্ট শুরু করা যায়নি। আবার চেষ্টা করুন।", "status_error" to "এখন পেমেন্টের অবস্থা দেখা যায়নি"
    ),
    "ur" to mapOf(
        "pay" to "ZAPUPI سے ادائیگی کریں", "creating" to "ادائیگی بنائی جا رہی ہے...", "manual" to "اس کے بجائے دستی UPI استعمال کریں",
        "verified_note" to "والیٹ میں رقم شامل کرنے سے پہلے سرور ZapUPI ادائیگی کی تصدیق کرے گا۔", "complete" to "ZapUPI ادائیگی مکمل کریں",
        "secure_info" to "محفوظ ZapUPI صفحے پر ادائیگی مکمل کریں۔ سرور کی تصدیق کے بعد ہی والیٹ میں رقم آئے گی۔",
        "open" to "محفوظ ادائیگی صفحہ کھولیں", "checking" to "جانچ جاری ہے...", "check" to "ادائیگی کی حالت دیکھیں", "close" to "بند کریں",
        "success" to "ادائیگی کی تصدیق ہوگئی۔ والیٹ اپ ڈیٹ ہوگیا۔", "failed" to "ادائیگی مکمل نہیں ہوئی۔ والیٹ میں رقم شامل نہیں ہوئی۔",
        "review" to "ادائیگی کی دستی جانچ ضروری ہے۔ ادائیگی کی ہو تو سپورٹ سے رابطہ کریں۔", "pending" to "ادائیگی ابھی زیرِ التوا ہے۔ کچھ دیر بعد دوبارہ دیکھیں۔",
        "open_error" to "ادائیگی صفحہ نہیں کھل سکا", "start_error" to "ادائیگی شروع نہیں ہوسکی۔ دوبارہ کوشش کریں۔", "status_error" to "ابھی ادائیگی کی حالت نہیں دیکھی جاسکی"
    ),
    "ne" to mapOf(
        "pay" to "ZAPUPI बाट भुक्तानी गर्नुहोस्", "creating" to "भुक्तानी बनाइँदैछ...", "manual" to "यसको सट्टा म्यानुअल UPI प्रयोग गर्नुहोस्",
        "verified_note" to "वालेटमा रकम थप्नुअघि सर्भरले ZapUPI भुक्तानी जाँच गर्छ।", "complete" to "ZapUPI भुक्तानी पूरा गर्नुहोस्",
        "secure_info" to "सुरक्षित ZapUPI पृष्ठमा भुक्तानी पूरा गर्नुहोस्। सर्भरले पुष्टि गरेपछि मात्र वालेटमा रकम थपिन्छ।",
        "open" to "सुरक्षित भुक्तानी पृष्ठ खोल्नुहोस्", "checking" to "जाँच हुँदैछ...", "check" to "भुक्तानी अवस्था जाँच्नुहोस्", "close" to "बन्द गर्नुहोस्",
        "success" to "भुक्तानी पुष्टि भयो। वालेट अद्यावधिक भयो।", "failed" to "भुक्तानी पूरा भएन। वालेटमा रकम थपिएन।",
        "review" to "भुक्तानी म्यानुअल रूपमा जाँच गर्नुपर्छ। भुक्तानी गर्नुभएको हो भने सहायता सम्पर्क गर्नुहोस्।", "pending" to "भुक्तानी अझै बाँकी छ। केही बेरपछि फेरि जाँच्नुहोस्।",
        "open_error" to "भुक्तानी पृष्ठ खोल्न सकिएन", "start_error" to "भुक्तानी सुरु भएन। फेरि प्रयास गर्नुहोस्।", "status_error" to "अहिले भुक्तानी अवस्था जाँच्न सकिएन"
    ),
    "ta" to mapOf(
        "pay" to "ZAPUPI மூலம் பணம் செலுத்து", "creating" to "பணம் செலுத்தல் உருவாகிறது...", "manual" to "மாற்றாக கைமுறை UPI பயன்படுத்தவும்",
        "verified_note" to "வாலெட்டில் பணம் சேர்ப்பதற்கு முன் சேவையகம் ZapUPI கட்டணத்தைச் சரிபார்க்கும்.", "complete" to "ZapUPI கட்டணத்தை முடிக்கவும்",
        "secure_info" to "பாதுகாப்பான ZapUPI பக்கத்தில் கட்டணத்தை முடிக்கவும். சேவையகச் சரிபார்ப்புக்குப் பிறகே வாலெட்டில் பணம் சேரும்.",
        "open" to "பாதுகாப்பான கட்டணப் பக்கத்தைத் திற", "checking" to "சரிபார்க்கிறது...", "check" to "கட்டண நிலையைச் சரிபார்க்கவும்", "close" to "மூடு",
        "success" to "கட்டணம் சரிபார்க்கப்பட்டது. வாலெட் புதுப்பிக்கப்பட்டது.", "failed" to "கட்டணம் நிறைவடையவில்லை. வாலெட்டில் பணம் சேர்க்கப்படவில்லை.",
        "review" to "கட்டணத்தை கைமுறையாகச் சரிபார்க்க வேண்டும். பணம் செலுத்தியிருந்தால் ஆதரவைத் தொடர்புகொள்ளவும்.", "pending" to "கட்டணம் இன்னும் நிலுவையில் உள்ளது. சிறிது நேரத்தில் மீண்டும் பார்க்கவும்.",
        "open_error" to "கட்டணப் பக்கத்தைத் திறக்க முடியவில்லை", "start_error" to "கட்டணத்தைத் தொடங்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்.", "status_error" to "இப்போது கட்டண நிலையைச் சரிபார்க்க முடியவில்லை"
    ),
    "ar" to mapOf(
        "pay" to "الدفع عبر ZAPUPI", "creating" to "جارٍ إنشاء عملية الدفع...", "manual" to "استخدم UPI يدويًا بدلًا من ذلك",
        "verified_note" to "يتحقق الخادم من دفعة ZapUPI قبل إضافة الرصيد إلى المحفظة.", "complete" to "أكمل الدفع عبر ZapUPI",
        "secure_info" to "أكمل الدفع على صفحة ZapUPI الآمنة. لن يضاف الرصيد إلا بعد تحقق الخادم.",
        "open" to "افتح صفحة الدفع الآمنة", "checking" to "جارٍ التحقق...", "check" to "تحقق من حالة الدفع", "close" to "إغلاق",
        "success" to "تم التحقق من الدفع. تم تحديث المحفظة.", "failed" to "لم تكتمل عملية الدفع. لم تتم إضافة رصيد.",
        "review" to "تحتاج الدفعة إلى مراجعة يدوية. تواصل مع الدعم إذا كنت قد دفعت.", "pending" to "لا تزال الدفعة معلقة. تحقق مرة أخرى بعد قليل.",
        "open_error" to "تعذر فتح صفحة الدفع", "start_error" to "تعذر بدء الدفع. حاول مرة أخرى.", "status_error" to "تعذر التحقق من حالة الدفع الآن"
    )
)

fun zapupiText(key: String): String = zapupiTranslations[AppLanguage.current]?.get(key)
    ?: zapupiTranslations["en"]?.get(key) ?: key
