package net.tjalp.nexus.lang

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.TranslationStore
import net.kyori.adventure.util.UTF8ResourceBundleControl.utf8ResourceBundleControl
import net.tjalp.nexus.NexusPlugin
import java.net.URLClassLoader
import java.util.*

/**
 * Language utilities and translation store.
 */
object Lang {

    /**
     * The translation store key for Nexus.
     */
    val TRANSLATION_STORE_KEY = Key.key(NexusPlugin, "translation_store")

    /**
     * The MiniMessage translation store for Nexus.
     */
    var translationStore: TranslationStore.StringBased<String> =
        MiniMessageTranslationStore.create(TRANSLATION_STORE_KEY)
        private set

    /**
     * Initializes the language system by registering the translation store.
     */
    fun init() {
        require(!GlobalTranslator.translator().sources().contains(translationStore)) {
            "Translation store for Nexus is already registered!"
        }

        loadTranslations(translationStore)
        GlobalTranslator.translator().addSource(translationStore)
    }

    /**
     * Reloads the translations from resource bundles and updates the translation store.
     */
    fun reload() {
        val previous = translationStore

        translationStore = MiniMessageTranslationStore.create(TRANSLATION_STORE_KEY)

        loadTranslations(translationStore)

        GlobalTranslator.translator().apply {
            removeSource(previous)
            addSource(translationStore)
        }
    }

    private fun loadTranslations(store: TranslationStore.StringBased<String>) {
        val allowedLocales = listOf(
            Locale.US,
            Locale.of("nl", "NL")
        )

        for (locale in allowedLocales) {
            val bundle = ResourceBundle.getBundle("l10n/messages", locale, utf8ResourceBundleControl())
            val path = NexusPlugin.dataPath.resolve("lang").toUri().toURL()
            val dataPathBundle = URLClassLoader(arrayOf(path)).use { loader ->
                ResourceBundle.getBundle("translations", locale, loader, utf8ResourceBundleControl())
            }

            store.registerAll(locale, bundle, false)
            store.registerAll(locale, dataPathBundle, false)
        }
    }
}