package com.gamehac.perappscaler.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.gamehac.perappscaler.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object LocaleHelper {

    data class SupportedLanguage(
        val code: String,
        val displayNameResId: Int
    )

    val LANGUAGES = listOf(
        SupportedLanguage("", R.string.lang_system),
        SupportedLanguage("en", R.string.lang_en),
        SupportedLanguage("es", R.string.lang_es),
        SupportedLanguage("pt", R.string.lang_pt),
        SupportedLanguage("in", R.string.lang_in),
        SupportedLanguage("vi", R.string.lang_vi),
        SupportedLanguage("ru", R.string.lang_ru),
        SupportedLanguage("zh-CN", R.string.lang_zh_cn),
        SupportedLanguage("zh-TW", R.string.lang_zh_tw),
        SupportedLanguage("ar", R.string.lang_ar)
    )

    fun showLanguageDialog(context: Context, onLanguageSelected: (String) -> Unit) {
        val names = LANGUAGES.map { context.getString(it.displayNameResId) }.toTypedArray()
        val currentTag = getCurrentLanguageTag()
        val currentIndex = LANGUAGES.indexOfFirst { it.code.equals(currentTag, ignoreCase = true) }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_select_language)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val selected = LANGUAGES[which]
                setApplicationLanguage(selected.code)
                onLanguageSelected(selected.code)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    fun setApplicationLanguage(languageCode: String) {
        val localeList = if (languageCode.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getCurrentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (!locales.isEmpty) {
            locales.get(0)?.toLanguageTag() ?: ""
        } else {
            ""
        }
    }
}
