# Launcher localization

English in `messages.properties` is the complete fallback catalog. To add a locale, copy that file to the Java bundle
name for its BCP 47 tag (for example, `messages_pt_BR.properties`), translate values without changing keys or `{0}` placeholders, and add the tag plus
its native display name to `supported-locales.properties`. Keep controls wide enough for translated text and test both
an expanded locale and an RTL locale. Locale selection is persisted in launcher settings; unsupported or incomplete
catalogs fall back to English per key.
