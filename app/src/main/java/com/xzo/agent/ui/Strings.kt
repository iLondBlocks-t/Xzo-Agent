package com.xzo.agent.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * Lightweight in-app localisation.
 *
 * A full `strings.xml` split would scatter the copy across two files for a
 * Compose-only UI; this keeps every phrase and its translation side by side,
 * costs nothing at runtime, and follows the device language automatically.
 */
data class XzoStrings(
    val rtl: Boolean = false,

    // composer
    val askPlaceholder: String = "Ask Xzo anything…",
    val attachFile: String = "Attach a file",
    val attachImage: String = "Attach an image",
    val takePhoto: String = "Take a photo",
    val voiceInput: String = "Voice input",
    val send: String = "Send",
    val stop: String = "Stop",

    // modes
    val modeChat: String = "Chat",
    val modeAgent: String = "Agent",
    val modeResearch: String = "Deep Research",

    // empty state
    val tagline: String = "Plan · Act · Observe · Verify",
    val freeNote: String = "Free and unlimited: Xzo never charges you, never shows ads and sets no quota of its own.",
    val next: String = "Next",
    val thinking: String = "Thinking…",
    val planning: String = "Planning…",
    val verifying: String = "Verifying answer…",
    val verified: String = "Verified",

    // navigation
    val newChat: String = "New chat",
    val chats: String = "Chats",
    val settings: String = "Settings",
    val promptLibrary: String = "Prompt library",
    val workspace: String = "Workspace",
    val automations: String = "Automations",
    val searchMessages: String = "Search messages",
    val exportMarkdown: String = "Export as markdown",
    val exportPdf: String = "Export as PDF",
    val clearMessages: String = "Clear messages",

    // connect
    val connectTitle: String = "Connect Xzo",
    val connectLead: String = "Xzo thinks with a cloud brain, and that needs one access key.",
    val connectPrivacy: String = "The key is stored only on this phone. Xzo never uploads it anywhere except to the " +
        "compute route that answers your questions, and it is never shown in a chat.",
    val primaryKey: String = "Primary access key",
    val primaryKeyHint: String = "Powers thinking, live web browsing, the code sandbox and voice input.",
    val backupKey: String = "Backup access key (optional)",
    val backupKeyHint: String = "Used automatically whenever the primary route is busy or unavailable.",
    val pasteKey: String = "Paste the key",
    val saveAndTest: String = "Save & test connection",
    val testing: String = "Testing…",
    val clear: String = "Clear",
    val notSet: String = "Not set",
    val startUsing: String = "Start using Xzo",
    val continueOffline: String = "Continue offline",
    val troubleTitle: String = "If a key keeps getting refused",
    val offlineTitle: String = "Works with no key at all",
    val offlineBody: String = "Reading text out of photos and PDFs, translating between 50+ languages, " +
        "detecting a language, the exact calculator, your saved files, memory, backups and every past " +
        "conversation — all of that runs on the device itself.",
    val notConnected: String = "Xzo is not connected yet",
    val notConnectedBody: String = "Tap here to paste an access key and test the connection. On-device features — " +
        "reading photos and PDFs, offline translation, the calculator — already work.",

    // key states
    val stateConnected: String = "Connected",
    val stateMissing: String = "No key saved",
    val stateMalformed: String = "Key looks incomplete",
    val stateRejected: String = "Key refused",
    val stateBusy: String = "Key valid · route busy",
    val stateOffline: String = "No network",
    val stateUnknown: String = "Unexpected response"
)

private val Arabic = XzoStrings(
    rtl = true,
    askPlaceholder = "اسأل Xzo أي شيء…",
    attachFile = "إرفاق ملف",
    attachImage = "إرفاق صورة",
    takePhoto = "التقاط صورة",
    voiceInput = "إدخال صوتي",
    send = "إرسال",
    stop = "إيقاف",

    modeChat = "محادثة",
    modeAgent = "وكيل",
    modeResearch = "بحث معمّق",

    tagline = "تخطيط · تنفيذ · ملاحظة · تحقّق",
    freeNote = "مجاني وبلا حدود: Xzo لا يفرض أي رسوم ولا إعلانات ولا حصة استخدام.",
    next = "التالي",
    thinking = "يفكر…",
    planning = "يخطط…",
    verifying = "يتحقق من الإجابة…",
    verified = "تم التحقق",

    newChat = "محادثة جديدة",
    chats = "المحادثات",
    settings = "الإعدادات",
    promptLibrary = "مكتبة الأوامر",
    workspace = "مساحة العمل",
    automations = "المهام التلقائية",
    searchMessages = "ابحث في الرسائل",
    exportMarkdown = "تصدير كملف Markdown",
    exportPdf = "تصدير كملف PDF",
    clearMessages = "مسح الرسائل",

    connectTitle = "توصيل Xzo",
    connectLead = "يحتاج Xzo إلى مفتاح وصول واحد للتفكير عبر السحابة.",
    connectPrivacy = "يُحفظ المفتاح على هذا الهاتف فقط، ولا يُرسل إلى أي جهة سوى خدمة الحوسبة التي تجيب على أسئلتك، ولا يظهر أبداً في المحادثة.",
    primaryKey = "مفتاح الوصول الأساسي",
    primaryKeyHint = "يشغّل التفكير وتصفح الويب المباشر وتشغيل الأكواد والإدخال الصوتي.",
    backupKey = "مفتاح الوصول الاحتياطي (اختياري)",
    backupKeyHint = "يُستخدم تلقائياً عندما يكون المسار الأساسي مشغولاً أو غير متاح.",
    pasteKey = "ألصق المفتاح",
    saveAndTest = "حفظ واختبار الاتصال",
    testing = "جارٍ الاختبار…",
    clear = "مسح",
    notSet = "غير محدد",
    startUsing = "ابدأ استخدام Xzo",
    continueOffline = "المتابعة دون اتصال",
    troubleTitle = "إذا استمر رفض المفتاح",
    offlineTitle = "يعمل بدون أي مفتاح",
    offlineBody = "قراءة النصوص من الصور وملفات PDF، والترجمة بين أكثر من ٥٠ لغة، وتحديد اللغة، والآلة الحاسبة الدقيقة، وملفاتك المحفوظة والذاكرة والنسخ الاحتياطية وكل المحادثات السابقة — كل ذلك يعمل على الجهاز نفسه.",
    notConnected = "لم يتم توصيل Xzo بعد",
    notConnectedBody = "اضغط هنا للصق مفتاح الوصول واختبار الاتصال. الميزات المحلية — قراءة الصور وملفات PDF والترجمة دون إنترنت والآلة الحاسبة — تعمل بالفعل.",

    stateConnected = "متصل",
    stateMissing = "لا يوجد مفتاح محفوظ",
    stateMalformed = "المفتاح يبدو ناقصاً",
    stateRejected = "تم رفض المفتاح",
    stateBusy = "المفتاح صالح · المسار مزدحم",
    stateOffline = "لا يوجد اتصال بالشبكة",
    stateUnknown = "استجابة غير متوقعة"
)

val LocalStrings = staticCompositionLocalOf { XzoStrings() }

@Composable
fun rememberStrings(): XzoStrings {
    val config = LocalConfiguration.current
    val language = runCatching { config.locales[0].language }.getOrDefault(Locale.getDefault().language)
    return if (language.equals("ar", ignoreCase = true)) Arabic else XzoStrings()
}

val troubleTipsEn = listOf(
    "Copy the whole key — they are long, and a half-copied key always fails.",
    "Remove any spaces or line breaks that came along with it.",
    "A key that was ever posted publicly is disabled automatically. Make a new one.",
    "A brand-new key can take a few seconds to become active."
)

val troubleTipsAr = listOf(
    "انسخ المفتاح كاملاً — فهو طويل، والنسخ الجزئي يفشل دائماً.",
    "احذف أي مسافات أو أسطر جديدة نُسخت معه.",
    "أي مفتاح نُشر علناً يتم تعطيله تلقائياً؛ أنشئ مفتاحاً جديداً.",
    "قد يحتاج المفتاح الجديد بضع ثوانٍ ليصبح فعّالاً."
)
