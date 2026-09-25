package com.xzo.agent.core

data class PromptTemplate(
    val id: String,
    val title: String,
    val category: String,
    /** Stable key resolved to a vector icon by the UI layer. No emoji anywhere. */
    val icon: String,
    val prompt: String,
    val usesTools: List<String> = emptyList()
)

/**
 * Curated prompt packs. Every entry is written to exercise the agent loop
 * (search → compute → write a file → verify) rather than plain chat.
 */
object PromptLibrary {

    val categories = listOf("Research", "Files", "Coding", "Productivity", "Learning", "Arabic")

    val all: List<PromptTemplate> = listOf(
        // ---------------- Research ----------------
        PromptTemplate(
            "news_today", "Today's briefing", "Research", "news",
            "Search the web for the most important news in technology and AI from the last 24 hours. " +
                "Give me 6 bullets, each with one sentence of context and the source URL. End with what it means for me.",
            listOf("web_search")
        ),
        PromptTemplate(
            "compare_products", "Compare two products", "Research", "compare",
            "Compare [PRODUCT A] and [PRODUCT B]. Search for current prices, specs and recent reviews, " +
                "then give me a markdown table plus a one-paragraph recommendation with sources.",
            listOf("web_search", "fetch_url")
        ),
        PromptTemplate(
            "fact_check", "Fact-check a claim", "Research", "verify",
            "Fact-check this claim: \"[CLAIM]\". Search for primary sources, state whether it is true, " +
                "false or partly true, and list the evidence with URLs. Flag anything you could not verify.",
            listOf("web_search")
        ),
        PromptTemplate(
            "deep_dive", "Deep dive report", "Research", "research",
            "Research [TOPIC] thoroughly: history, current state, key players, numbers, and what changes next. " +
                "Use several searches, then save the result as a markdown report file on my device.",
            listOf("web_search", "create_file")
        ),

        // ---------------- Files ----------------
        PromptTemplate(
            "budget_csv", "Monthly budget CSV", "Files", "table",
            "Create a 12-month personal budget spreadsheet as a CSV: columns for month, income, rent, food, " +
                "transport, utilities, savings and balance, with realistic placeholder numbers and a totals row. " +
                "Save it to my device.",
            listOf("create_file", "code_execution")
        ),
        PromptTemplate(
            "summarize_file", "Summarise a document", "Files", "document",
            "Read the file I attach and give me: a 5-bullet summary, the key numbers, any risks or open questions, " +
                "and 3 concrete next actions.",
            listOf("read_file")
        ),
        PromptTemplate(
            "convert_file", "Convert & clean data", "Files", "convert",
            "Read the file I attach, clean the data (trim whitespace, fix inconsistent casing, drop empty rows), " +
                "convert it to JSON, and save the result as a new file.",
            listOf("read_file", "code_execution", "create_file")
        ),

        // ---------------- Coding ----------------
        PromptTemplate(
            "explain_code", "Explain this code", "Coding", "explain",
            "Read the code file I attach and explain it: what it does overall, then a walkthrough of each " +
                "important function, the edge cases it misses, and how I would test it.",
            listOf("read_file")
        ),
        PromptTemplate(
            "build_script", "Write a script", "Coding", "script",
            "Write a well-commented Python script that [GOAL]. Test the core logic with the code sandbox, " +
                "then save the final script to my device as a .py file.",
            listOf("code_execution", "create_file")
        ),
        PromptTemplate(
            "debug", "Debug an error", "Coding", "debug",
            "I get this error:\n\n[PASTE ERROR]\n\nSearch for the current known causes, explain what is actually " +
                "happening, and give me the exact fix with code.",
            listOf("web_search")
        ),
        PromptTemplate(
            "regex", "Build a regex", "Coding", "regex",
            "Build a regular expression that matches [DESCRIBE]. Test it against at least 6 example strings " +
                "using the code sandbox and show the results table.",
            listOf("code_execution")
        ),

        // ---------------- Productivity ----------------
        PromptTemplate(
            "plan_week", "Plan my week", "Productivity", "calendar",
            "Here are my tasks: [LIST]. Check today's date, then build a realistic day-by-day plan for the " +
                "coming week with time blocks, priorities and buffer time. Save it as a markdown checklist file.",
            listOf("current_datetime", "create_file")
        ),
        PromptTemplate(
            "email", "Draft a message", "Productivity", "message",
            "Draft a professional message about [TOPIC] to [RECIPIENT]. Keep it under 150 words, " +
                "give me three tone variants: direct, warm, and formal.",
            emptyList()
        ),
        PromptTemplate(
            "meeting_notes", "Turn notes into actions", "Productivity", "checklist",
            "Here are my raw meeting notes: [PASTE]. Turn them into a clean summary, a decision log, " +
                "and an owner/deadline action table. Save the result as a file.",
            listOf("create_file")
        ),

        // ---------------- Learning ----------------
        PromptTemplate(
            "explain_simple", "Explain like I'm new", "Learning", "learn",
            "Explain [TOPIC] from zero: the intuition first, then the mechanics, then one worked example, " +
                "then three questions to test whether I understood.",
            emptyList()
        ),
        PromptTemplate(
            "study_plan", "30-day study plan", "Learning", "plan",
            "Build a 30-day study plan to learn [SKILL] at 1 hour a day. Search for the best current free " +
                "resources, then save the plan as a markdown file with checkboxes.",
            listOf("web_search", "create_file")
        ),
        PromptTemplate(
            "flashcards", "Make flashcards", "Learning", "cards",
            "Create 25 spaced-repetition flashcards about [TOPIC] as a CSV with 'front' and 'back' columns, " +
                "then save it so I can import it into Anki.",
            listOf("create_file")
        ),

        // ---------------- Arabic ----------------
        PromptTemplate(
            "ar_news", "أخبار اليوم", "Arabic", "news",
            "ابحث في الإنترنت عن أهم أخبار التقنية والذكاء الاصطناعي خلال آخر 24 ساعة، " +
                "واكتب لي ملخصاً من ست نقاط مع رابط المصدر لكل نقطة.",
            listOf("web_search")
        ),
        PromptTemplate(
            "ar_summary", "لخص هذا الملف", "Arabic", "document",
            "اقرأ الملف المرفق ولخصه بالعربية في خمس نقاط، مع ذكر الأرقام المهمة وثلاث خطوات عملية تالية.",
            listOf("read_file")
        ),
        PromptTemplate(
            "ar_translate", "ترجمة احترافية", "Arabic", "translate",
            "ترجم النص التالي إلى العربية الفصحى ترجمة احترافية مع الحفاظ على المصطلحات التقنية بالإنجليزية:\n\n[النص]",
            emptyList()
        ),
        PromptTemplate(
            "ar_plan", "خطة أسبوعية", "Arabic", "calendar",
            "تحقق من تاريخ اليوم ثم اصنع لي خطة أسبوعية واقعية للمهام التالية: [المهام]. " +
                "احفظ الخطة في ملف على هاتفي.",
            listOf("current_datetime", "create_file")
        )
    )

    fun byCategory(category: String): List<PromptTemplate> = all.filter { it.category == category }
}
