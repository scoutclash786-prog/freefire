package com.clash.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class AppLanguageOption(val code: String, val label: String, val nativeName: String)

/** Lightweight persisted UI localization for the app's shared navigation and support surfaces. */
object AppLanguage {
    val options = listOf(
        AppLanguageOption("en", "English", "English"),
        AppLanguageOption("hi", "Hindi", "हिन्दी"),
        AppLanguageOption("bn", "Bengali", "বাংলা"),
        AppLanguageOption("ur", "Urdu", "اردو"),
        AppLanguageOption("ne", "Nepali", "नेपाली"),
        AppLanguageOption("ta", "Tamil", "தமிழ்"),
        AppLanguageOption("ar", "Arabic", "العربية")
    )

    var current by mutableStateOf("en")
        private set

    private val english = mapOf(
        "choose_language" to "Choose language", "continue" to "CONTINUE",
        "tab_earn" to "Earn", "tab_rank" to "Rank", "tab_home" to "Home", "tab_wallet" to "Wallet", "tab_account" to "Account",
        "change_language" to "Change language", "push_notifications" to "Push notifications", "my_profile" to "My profile",
        "my_wallet" to "My wallet", "my_matches" to "My matches", "store" to "Store", "my_orders" to "My orders",
        "statistics" to "My statistics", "rewards" to "My rewards", "referrals" to "My referrals", "announcements" to "Announcements",
        "tutorial" to "App tutorial", "about" to "About us", "customer_support" to "Customer support", "share_app" to "Share app",
        "terms" to "Terms & conditions", "logout" to "Log out", "matches_played" to "Matches played", "total_kills" to "Total kills",
        "amount_won" to "Amount won", "kyc_verified" to "KYC verified", "kyc_pending" to "KYC pending",
        "support_channels" to "Contact support", "address" to "Address", "email" to "Email", "whatsapp" to "WhatsApp",
        "instagram" to "Instagram", "telegram" to "Telegram", "phone" to "Phone", "street" to "Street", "hours" to "Hours",
        "support_empty" to "Support details will be added soon.", "developer" to "Developer", "developer_team_default" to "M4 ESPORTS Development Team",
        "developer_info_default" to "For app feedback, bugs, or technical questions.", "contact_developer" to "Contact developer",
        "contact_not_configured" to "Developer contact is not configured yet.",
        "faq" to "FAQs", "create_ticket" to "Create support ticket", "my_tickets" to "My tickets", "new_ticket" to "New support ticket",
        "ticket_subject" to "Subject", "ticket_category" to "Category", "describe_issue" to "Describe your issue", "send_ticket" to "Submit ticket",
        "send_reply" to "Send reply", "status" to "Status", "ticket_id" to "Ticket ID", "no_tickets" to "No tickets yet",
        "search_faq" to "Search FAQs", "last_updated" to "Last updated", "ticket_closed" to "This ticket is closed"
    )

    private val translations: Map<String, Map<String, String>> = mapOf(
        "en" to english,
        "hi" to mapOf(
            "choose_language" to "भाषा चुनें", "continue" to "आगे बढ़ें", "tab_earn" to "कमाएँ", "tab_rank" to "रैंकिंग", "tab_home" to "होम", "tab_wallet" to "वॉलेट", "tab_account" to "खाता",
            "change_language" to "भाषा बदलें", "push_notifications" to "पुश नोटिफ़िकेशन", "my_profile" to "मेरी प्रोफ़ाइल", "my_wallet" to "मेरा वॉलेट", "my_matches" to "मेरे मैच", "store" to "स्टोर", "my_orders" to "मेरे ऑर्डर",
            "statistics" to "मेरे आँकड़े", "rewards" to "मेरे इनाम", "referrals" to "मेरे रेफ़रल", "announcements" to "घोषणाएँ", "tutorial" to "ऐप ट्यूटोरियल", "about" to "हमारे बारे में", "customer_support" to "ग्राहक सहायता", "share_app" to "ऐप शेयर करें", "terms" to "नियम और शर्तें", "logout" to "लॉग आउट",
            "matches_played" to "खेले गए मैच", "total_kills" to "कुल किल", "amount_won" to "जीती गई राशि", "kyc_verified" to "KYC सत्यापित", "kyc_pending" to "KYC लंबित", "support_channels" to "सहायता से संपर्क करें", "address" to "पता", "email" to "ईमेल", "whatsapp" to "WhatsApp", "instagram" to "Instagram", "telegram" to "Telegram", "phone" to "फ़ोन", "street" to "सड़क", "hours" to "समय",
            "support_empty" to "सहायता की जानकारी जल्द जोड़ी जाएगी।", "developer" to "डेवलपर", "developer_team_default" to "M4 ESPORTS विकास टीम", "developer_info_default" to "ऐप सुझाव, बग या तकनीकी प्रश्नों के लिए संपर्क करें।", "contact_developer" to "डेवलपर से संपर्क करें", "contact_not_configured" to "डेवलपर संपर्क अभी सेट नहीं किया गया है।",
            "faq" to "अक्सर पूछे जाने वाले सवाल", "create_ticket" to "सहायता टिकट बनाएँ", "my_tickets" to "मेरे टिकट", "new_ticket" to "नया सहायता टिकट", "ticket_subject" to "विषय", "ticket_category" to "श्रेणी", "describe_issue" to "अपनी समस्या बताएँ", "send_ticket" to "टिकट भेजें", "send_reply" to "जवाब भेजें", "status" to "स्थिति", "ticket_id" to "टिकट आईडी", "no_tickets" to "अभी कोई टिकट नहीं", "search_faq" to "FAQ खोजें", "last_updated" to "अंतिम अपडेट", "ticket_closed" to "यह टिकट बंद है"
        ),
        "bn" to mapOf(
            "choose_language" to "ভাষা নির্বাচন করুন", "continue" to "এগিয়ে যান", "tab_earn" to "আয়", "tab_rank" to "র‍্যাঙ্কিং", "tab_home" to "হোম", "tab_wallet" to "ওয়ালেট", "tab_account" to "অ্যাকাউন্ট",
            "change_language" to "ভাষা পরিবর্তন", "push_notifications" to "পুশ বিজ্ঞপ্তি", "my_profile" to "আমার প্রোফাইল", "my_wallet" to "আমার ওয়ালেট", "my_matches" to "আমার ম্যাচ", "store" to "স্টোর", "my_orders" to "আমার অর্ডার",
            "statistics" to "আমার পরিসংখ্যান", "rewards" to "আমার পুরস্কার", "referrals" to "আমার রেফারেল", "announcements" to "ঘোষণা", "tutorial" to "অ্যাপ টিউটোরিয়াল", "about" to "আমাদের সম্পর্কে", "customer_support" to "গ্রাহক সহায়তা", "share_app" to "অ্যাপ শেয়ার করুন", "terms" to "শর্তাবলি", "logout" to "লগ আউট",
            "matches_played" to "খেলা ম্যাচ", "total_kills" to "মোট কিল", "amount_won" to "জেতা অর্থ", "kyc_verified" to "KYC যাচাই হয়েছে", "kyc_pending" to "KYC অপেক্ষমাণ", "support_channels" to "সহায়তায় যোগাযোগ করুন", "address" to "ঠিকানা", "email" to "ইমেইল", "whatsapp" to "WhatsApp", "instagram" to "Instagram", "telegram" to "Telegram", "phone" to "ফোন", "street" to "রাস্তা", "hours" to "সময়",
            "support_empty" to "সহায়তার তথ্য শীঘ্রই যোগ করা হবে।", "developer" to "ডেভেলপার", "developer_team_default" to "M4 ESPORTS ডেভেলপমেন্ট টিম", "developer_info_default" to "অ্যাপের মতামত, বাগ বা প্রযুক্তিগত প্রশ্নের জন্য যোগাযোগ করুন।", "contact_developer" to "ডেভেলপারের সাথে যোগাযোগ", "contact_not_configured" to "ডেভেলপার যোগাযোগ এখনও সেট করা হয়নি।",
            "faq" to "সাধারণ জিজ্ঞাসা", "create_ticket" to "সহায়তা টিকিট তৈরি করুন", "my_tickets" to "আমার টিকিট", "new_ticket" to "নতুন সহায়তা টিকিট", "ticket_subject" to "বিষয়", "ticket_category" to "বিভাগ", "describe_issue" to "আপনার সমস্যাটি লিখুন", "send_ticket" to "টিকিট জমা দিন", "send_reply" to "উত্তর পাঠান", "status" to "অবস্থা", "ticket_id" to "টিকিট আইডি", "no_tickets" to "এখনও কোনো টিকিট নেই", "search_faq" to "FAQ খুঁজুন", "last_updated" to "সর্বশেষ আপডেট", "ticket_closed" to "এই টিকিটটি বন্ধ"
        ),
        "ur" to mapOf(
            "choose_language" to "زبان منتخب کریں", "continue" to "جاری رکھیں", "tab_earn" to "کمائیں", "tab_rank" to "درجہ بندی", "tab_home" to "ہوم", "tab_wallet" to "والیٹ", "tab_account" to "اکاؤنٹ",
            "change_language" to "زبان تبدیل کریں", "push_notifications" to "پش اطلاعات", "my_profile" to "میری پروفائل", "my_wallet" to "میرا والیٹ", "my_matches" to "میرے میچز", "store" to "اسٹور", "my_orders" to "میرے آرڈرز",
            "statistics" to "میرے اعدادوشمار", "rewards" to "میرے انعامات", "referrals" to "میرے ریفرلز", "announcements" to "اعلانات", "tutorial" to "ایپ ٹیوٹوریل", "about" to "ہمارے بارے میں", "customer_support" to "کسٹمر سپورٹ", "share_app" to "ایپ شیئر کریں", "terms" to "شرائط و ضوابط", "logout" to "لاگ آؤٹ",
            "matches_played" to "کھیلے گئے میچ", "total_kills" to "کل کِل", "amount_won" to "جیتی گئی رقم", "kyc_verified" to "KYC تصدیق شدہ", "kyc_pending" to "KYC زیرِ التوا", "support_channels" to "سپورٹ سے رابطہ", "address" to "پتہ", "email" to "ای میل", "whatsapp" to "WhatsApp", "instagram" to "Instagram", "telegram" to "Telegram", "phone" to "فون", "street" to "گلی", "hours" to "اوقات",
            "support_empty" to "سپورٹ کی تفصیلات جلد شامل کی جائیں گی۔", "developer" to "ڈویلپر", "developer_team_default" to "M4 ESPORTS ڈویلپمنٹ ٹیم", "developer_info_default" to "ایپ کی رائے، خرابی یا تکنیکی سوال کے لیے رابطہ کریں۔", "contact_developer" to "ڈویلپر سے رابطہ کریں", "contact_not_configured" to "ڈویلپر کا رابطہ ابھی ترتیب نہیں دیا گیا۔",
            "faq" to "اکثر پوچھے گئے سوالات", "create_ticket" to "سپورٹ ٹکٹ بنائیں", "my_tickets" to "میرے ٹکٹس", "new_ticket" to "نیا سپورٹ ٹکٹ", "ticket_subject" to "موضوع", "ticket_category" to "قسم", "describe_issue" to "اپنا مسئلہ بتائیں", "send_ticket" to "ٹکٹ جمع کریں", "send_reply" to "جواب بھیجیں", "status" to "حالت", "ticket_id" to "ٹکٹ آئی ڈی", "no_tickets" to "ابھی کوئی ٹکٹ نہیں", "search_faq" to "FAQ تلاش کریں", "last_updated" to "آخری اپ ڈیٹ", "ticket_closed" to "یہ ٹکٹ بند ہے"
        ),
        "ne" to mapOf(
            "choose_language" to "भाषा छान्नुहोस्", "continue" to "अगाडि बढ्नुहोस्", "tab_earn" to "कमाउनुहोस्", "tab_rank" to "र्‍याङ्किङ", "tab_home" to "गृह", "tab_wallet" to "वालेट", "tab_account" to "खाता",
            "change_language" to "भाषा परिवर्तन", "push_notifications" to "पुश सूचना", "my_profile" to "मेरो प्रोफाइल", "my_wallet" to "मेरो वालेट", "my_matches" to "मेरा म्याच", "store" to "स्टोर", "my_orders" to "मेरा अर्डर",
            "statistics" to "मेरो तथ्याङ्क", "rewards" to "मेरा पुरस्कार", "referrals" to "मेरा रेफरल", "announcements" to "सूचनाहरू", "tutorial" to "एप ट्युटोरियल", "about" to "हाम्रो बारेमा", "customer_support" to "ग्राहक सहायता", "share_app" to "एप साझा गर्नुहोस्", "terms" to "नियम तथा सर्त", "logout" to "लगआउट",
            "matches_played" to "खेलेका म्याच", "total_kills" to "जम्मा किल", "amount_won" to "जितेको रकम", "kyc_verified" to "KYC प्रमाणित", "kyc_pending" to "KYC बाँकी", "support_channels" to "सहायतामा सम्पर्क", "address" to "ठेगाना", "email" to "इमेल", "whatsapp" to "WhatsApp", "instagram" to "Instagram", "telegram" to "Telegram", "phone" to "फोन", "street" to "सडक", "hours" to "समय",
            "support_empty" to "सहायताको विवरण चाँडै थपिनेछ।", "developer" to "डेभलपर", "developer_team_default" to "M4 ESPORTS विकास टोली", "developer_info_default" to "एपको सुझाव, त्रुटि वा प्राविधिक प्रश्नका लागि सम्पर्क गर्नुहोस्।", "contact_developer" to "डेभलपरलाई सम्पर्क गर्नुहोस्", "contact_not_configured" to "डेभलपर सम्पर्क अझै सेट गरिएको छैन।",
            "faq" to "बारम्बार सोधिने प्रश्नहरू", "create_ticket" to "सहायता टिकट बनाउनुहोस्", "my_tickets" to "मेरा टिकटहरू", "new_ticket" to "नयाँ सहायता टिकट", "ticket_subject" to "विषय", "ticket_category" to "वर्ग", "describe_issue" to "आफ्नो समस्या लेख्नुहोस्", "send_ticket" to "टिकट पठाउनुहोस्", "send_reply" to "जवाफ पठाउनुहोस्", "status" to "स्थिति", "ticket_id" to "टिकट आईडी", "no_tickets" to "अहिलेसम्म कुनै टिकट छैन", "search_faq" to "FAQ खोज्नुहोस्", "last_updated" to "अन्तिम अद्यावधिक", "ticket_closed" to "यो टिकट बन्द छ"
        ),
        "ta" to mapOf(
            "choose_language" to "மொழியைத் தேர்ந்தெடுக்கவும்", "continue" to "தொடரவும்", "tab_earn" to "வருமானம்", "tab_rank" to "தரவரிசை", "tab_home" to "முகப்பு", "tab_wallet" to "வாலெட்", "tab_account" to "கணக்கு",
            "change_language" to "மொழியை மாற்றவும்", "push_notifications" to "புஷ் அறிவிப்புகள்", "my_profile" to "என் சுயவிவரம்", "my_wallet" to "என் வாலெட்", "my_matches" to "என் போட்டிகள்", "store" to "கடை", "my_orders" to "என் ஆர்டர்கள்",
            "statistics" to "என் புள்ளிவிவரங்கள்", "rewards" to "என் வெகுமதிகள்", "referrals" to "என் பரிந்துரைகள்", "announcements" to "அறிவிப்புகள்", "tutorial" to "செயலி வழிகாட்டி", "about" to "எங்களைப் பற்றி", "customer_support" to "வாடிக்கையாளர் ஆதரவு", "share_app" to "செயலியைப் பகிரவும்", "terms" to "விதிமுறைகள்", "logout" to "வெளியேறு",
            "matches_played" to "விளையாடிய போட்டிகள்", "total_kills" to "மொத்த கில்கள்", "amount_won" to "வென்ற தொகை", "kyc_verified" to "KYC சரிபார்க்கப்பட்டது", "kyc_pending" to "KYC நிலுவையில்", "support_channels" to "ஆதரவைத் தொடர்புகொள்ளவும்", "address" to "முகவரி", "email" to "மின்னஞ்சல்", "whatsapp" to "WhatsApp", "instagram" to "Instagram", "telegram" to "Telegram", "phone" to "தொலைபேசி", "street" to "தெரு", "hours" to "நேரம்",
            "support_empty" to "ஆதரவு விவரங்கள் விரைவில் சேர்க்கப்படும்.", "developer" to "உருவாக்குநர்", "developer_team_default" to "M4 ESPORTS உருவாக்கக் குழு", "developer_info_default" to "செயலி கருத்து, பிழை அல்லது தொழில்நுட்ப கேள்விகளுக்கு தொடர்புகொள்ளவும்.", "contact_developer" to "உருவாக்குநரைத் தொடர்புகொள்ளவும்", "contact_not_configured" to "உருவாக்குநர் தொடர்பு இன்னும் அமைக்கப்படவில்லை.",
            "faq" to "அடிக்கடி கேட்கப்படும் கேள்விகள்", "create_ticket" to "ஆதரவு டிக்கெட் உருவாக்கு", "my_tickets" to "என் டிக்கெட்டுகள்", "new_ticket" to "புதிய ஆதரவு டிக்கெட்", "ticket_subject" to "தலைப்பு", "ticket_category" to "வகை", "describe_issue" to "உங்கள் சிக்கலை விவரிக்கவும்", "send_ticket" to "டிக்கெட்டை சமர்ப்பி", "send_reply" to "பதிலை அனுப்பு", "status" to "நிலை", "ticket_id" to "டிக்கெட் எண்", "no_tickets" to "டிக்கெட்டுகள் இல்லை", "search_faq" to "FAQ-ஐத் தேடு", "last_updated" to "கடைசியாக புதுப்பிக்கப்பட்டது", "ticket_closed" to "இந்த டிக்கெட் மூடப்பட்டது"
        ),
        "ar" to mapOf(
            "choose_language" to "اختر اللغة", "continue" to "متابعة", "tab_earn" to "الأرباح", "tab_rank" to "التصنيف", "tab_home" to "الرئيسية", "tab_wallet" to "المحفظة", "tab_account" to "الحساب",
            "change_language" to "تغيير اللغة", "push_notifications" to "إشعارات فورية", "my_profile" to "ملفي الشخصي", "my_wallet" to "محفظتي", "my_matches" to "مبارياتي", "store" to "المتجر", "my_orders" to "طلباتي",
            "statistics" to "إحصائياتي", "rewards" to "مكافآتي", "referrals" to "إحالاتي", "announcements" to "الإعلانات", "tutorial" to "شرح التطبيق", "about" to "معلومات عنا", "customer_support" to "دعم العملاء", "share_app" to "مشاركة التطبيق", "terms" to "الشروط والأحكام", "logout" to "تسجيل الخروج",
            "matches_played" to "المباريات المكتملة", "total_kills" to "إجمالي الإقصاءات", "amount_won" to "المبلغ الذي فزت به", "kyc_verified" to "تم التحقق من الهوية", "kyc_pending" to "التحقق قيد الانتظار", "support_channels" to "تواصل مع الدعم", "address" to "العنوان", "email" to "البريد الإلكتروني", "whatsapp" to "واتساب", "instagram" to "إنستغرام", "telegram" to "تلغرام", "phone" to "الهاتف", "street" to "الشارع", "hours" to "ساعات العمل",
            "support_empty" to "ستتم إضافة تفاصيل الدعم قريبًا.", "developer" to "المطور", "developer_team_default" to "فريق تطوير M4 ESPORTS", "developer_info_default" to "لإرسال الملاحظات أو الإبلاغ عن الأخطاء أو الاستفسارات التقنية.", "contact_developer" to "تواصل مع المطور", "contact_not_configured" to "لم يتم إعداد وسيلة تواصل المطور بعد.",
            "faq" to "الأسئلة الشائعة", "create_ticket" to "إنشاء تذكرة دعم", "my_tickets" to "تذاكري", "new_ticket" to "تذكرة دعم جديدة", "ticket_subject" to "الموضوع", "ticket_category" to "الفئة", "describe_issue" to "اشرح مشكلتك", "send_ticket" to "إرسال التذكرة", "send_reply" to "إرسال الرد", "status" to "الحالة", "ticket_id" to "رقم التذكرة", "no_tickets" to "لا توجد تذاكر بعد", "search_faq" to "ابحث في الأسئلة الشائعة", "last_updated" to "آخر تحديث", "ticket_closed" to "هذه التذكرة مغلقة"
        )
    )

    fun load(context: Context) {
        val saved = context.getSharedPreferences("clash2_preferences", Context.MODE_PRIVATE).getString("language", "en") ?: "en"
        current = options.firstOrNull { it.code == saved }?.code ?: "en"
    }

    fun select(context: Context, languageCode: String) {
        if (options.none { it.code == languageCode }) return
        current = languageCode
        context.getSharedPreferences("clash2_preferences", Context.MODE_PRIVATE).edit().putString("language", languageCode).apply()
    }

    fun text(key: String): String = translations[current]?.get(key) ?: english[key] ?: key
    val isRtl: Boolean get() = current == "ur" || current == "ar"
}

fun tr(key: String): String = AppLanguage.text(key)

fun brandAppName(text: String): String = Regex("(?i)\\b(?:clash\\s*(?:2(?:\\.0)?|admin)|m4\\s*esports|scoutclash)\\b").replace(text, "EliteXarena")
