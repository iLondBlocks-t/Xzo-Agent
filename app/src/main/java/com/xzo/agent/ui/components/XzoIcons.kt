package com.xzo.agent.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import com.xzo.agent.agent.Role

/**
 * Single source of truth for iconography.
 *
 * The app deliberately contains **no emoji**: every glyph is a real vector icon so
 * it scales, tints with the theme, respects RTL mirroring and renders identically
 * on every OEM font stack.
 */
object XzoIcons {

    /** Icon for each specialist agent. */
    fun forRole(role: Role): ImageVector = when (role) {
        Role.PLANNER -> Icons.Rounded.Explore
        Role.RESEARCHER -> Icons.Rounded.Search
        Role.ANALYST -> Icons.Rounded.Insights
        Role.CODER -> Icons.Outlined.Terminal
        Role.WRITER -> Icons.Rounded.Edit
        Role.TRANSLATOR -> Icons.Rounded.Translate
        Role.CRITIC -> Icons.Rounded.Biotech
        Role.VISION -> Icons.Rounded.RemoveRedEye
    }

    /** Icon for a prompt-library entry, resolved from its stable key. */
    fun forPrompt(key: String): ImageVector = when (key) {
        "news" -> Icons.Outlined.Newspaper
        "compare" -> Icons.Outlined.Balance
        "verify" -> Icons.Outlined.FactCheck
        "research" -> Icons.Outlined.AutoStories
        "table" -> Icons.Outlined.TableChart
        "document" -> Icons.Outlined.Description
        "convert" -> Icons.Outlined.SwapHoriz
        "explain" -> Icons.Outlined.Extension
        "script" -> Icons.Outlined.Code
        "debug" -> Icons.Outlined.BugReport
        "regex" -> Icons.Outlined.Tag
        "calendar" -> Icons.Outlined.CalendarMonth
        "message" -> Icons.Outlined.Mail
        "checklist" -> Icons.Outlined.Checklist
        "learn" -> Icons.Outlined.School
        "plan" -> Icons.Outlined.EventNote
        "cards" -> Icons.Outlined.Style
        "translate" -> Icons.Outlined.Language
        else -> Icons.Outlined.Article
    }

    /** Icon shown next to a tool call in the agent trace. */
    fun forTool(name: String): ImageVector = when {
        name.contains("search") || name.contains("browse") -> Icons.Rounded.Search
        name.contains("fetch") || name.contains("url") -> Icons.Outlined.Language
        name.contains("code") || name.contains("calc") -> Icons.Outlined.Terminal
        name.contains("file") || name.contains("folder") -> Icons.Outlined.Description
        name.contains("image") || name.contains("ocr") -> Icons.Rounded.RemoveRedEye
        name.contains("translate") || name.contains("language") -> Icons.Rounded.Translate
        name.contains("remember") || name.contains("recall") -> Icons.Outlined.AutoStories
        name.contains("delegate") -> Icons.Rounded.Explore
        name.contains("summar") -> Icons.Outlined.Article
        else -> Icons.Outlined.Extension
    }
}
