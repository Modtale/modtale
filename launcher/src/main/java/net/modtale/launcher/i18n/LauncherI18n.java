package net.modtale.launcher.i18n;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.NodeOrientation;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;

/** Lightweight, runtime-switchable localization for the native launcher. */
public final class LauncherI18n {

    private static final String BUNDLE_NAME = "net.modtale.launcher.i18n.messages";
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final ResourceBundle.Control NO_SYSTEM_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final LauncherI18n INSTANCE = new LauncherI18n(loadSupportedLocales());

    private final List<LocaleOption> supportedLocales;
    private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>();
    private ResourceBundle bundle;

    LauncherI18n(List<LocaleOption> supportedLocales) {
        this.supportedLocales = List.copyOf(supportedLocales);
        setLocale(Locale.getDefault());
    }

    public static LauncherI18n get() {
        return INSTANCE;
    }

    public List<LocaleOption> supportedLocales() {
        return supportedLocales;
    }

    public ReadOnlyObjectProperty<Locale> localeProperty() {
        return locale;
    }

    public Locale locale() {
        return locale.get();
    }

    public String localeTag() {
        return locale().toLanguageTag();
    }

    public void setLocale(String languageTag) {
        setLocale(normalize(languageTag));
    }

    public void setLocale(Locale requested) {
        Locale resolved = resolveSupported(requested);
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, resolved, NO_SYSTEM_FALLBACK);
        locale.set(resolved);
        Locale.setDefault(Locale.Category.FORMAT, resolved);
    }

    public String text(String key, Object... arguments) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException missing) {
            ResourceBundle fallback = ResourceBundle.getBundle(BUNDLE_NAME, DEFAULT_LOCALE, NO_SYSTEM_FALLBACK);
            try {
                pattern = fallback.getString(key);
            } catch (MissingResourceException absentFromEnglishCatalog) {
                return '!' + key + '!';
            }
        }
        return arguments.length == 0 ? pattern : new MessageFormat(pattern, locale()).format(arguments);
    }

    public String plural(String key, long count, Object... arguments) {
        String category = count == 1 ? ".one" : ".other";
        Object[] values = new Object[arguments.length + 1];
        values[0] = count;
        System.arraycopy(arguments, 0, values, 1, arguments.length);
        return text(key + category, values);
    }

    public String number(Number value) {
        return NumberFormat.getNumberInstance(locale()).format(value);
    }

    public String date(LocalDate value) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()).format(value);
    }

    public StringBinding binding(String key, Object... arguments) {
        return Bindings.createStringBinding(() -> text(key, arguments), locale);
    }

    public void bind(Labeled node, String key, Object... arguments) {
        node.textProperty().bind(binding(key, arguments));
    }

    public Tooltip tooltip(String key, Object... arguments) {
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(binding(key, arguments));
        return tooltip;
    }

    public void applyDirection(Parent root) {
        root.setNodeOrientation(isRightToLeft(locale())
                ? NodeOrientation.RIGHT_TO_LEFT
                : NodeOrientation.LEFT_TO_RIGHT);
        locale.addListener((ignored, previous, current) -> root.setNodeOrientation(isRightToLeft(current)
                ? NodeOrientation.RIGHT_TO_LEFT
                : NodeOrientation.LEFT_TO_RIGHT));
    }

    public static Locale normalize(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return DEFAULT_LOCALE;
        }
        Locale parsed = Locale.forLanguageTag(languageTag.trim().replace('_', '-'));
        return parsed.getLanguage().isBlank() ? DEFAULT_LOCALE : parsed;
    }

    private Locale resolveSupported(Locale requested) {
        Locale normalized = requested == null ? DEFAULT_LOCALE : normalize(requested.toLanguageTag());
        return supportedLocales.stream()
                .map(LocaleOption::locale)
                .filter(candidate -> candidate.equals(normalized))
                .findFirst()
                .or(() -> supportedLocales.stream()
                        .map(LocaleOption::locale)
                        .filter(candidate -> candidate.getLanguage().equals(normalized.getLanguage()))
                        .findFirst())
                .orElse(DEFAULT_LOCALE);
    }

    private static boolean isRightToLeft(Locale locale) {
        return switch (locale.getLanguage()) {
            case "ar", "dv", "fa", "he", "ku", "ps", "ur", "yi" -> true;
            default -> false;
        };
    }

    private static List<LocaleOption> loadSupportedLocales() {
        ResourceBundle metadata = ResourceBundle.getBundle(
                "net.modtale.launcher.i18n.supported-locales", Locale.ROOT, NO_SYSTEM_FALLBACK);
        List<LocaleOption> locales = new ArrayList<>();
        for (String tag : metadata.getString("locales").split(",")) {
            Locale locale = normalize(tag);
            locales.add(new LocaleOption(locale, metadata.getString(locale.toLanguageTag() + ".name")));
        }
        return locales.isEmpty() ? List.of(new LocaleOption(DEFAULT_LOCALE, "English")) : locales;
    }

    public record LocaleOption(Locale locale, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }
}
