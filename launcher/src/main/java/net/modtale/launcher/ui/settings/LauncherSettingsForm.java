package net.modtale.launcher.ui.settings;

import static net.modtale.launcher.ui.common.LauncherUi.styleCombo;
import static net.modtale.launcher.ui.common.LauncherUi.styleInput;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.hytale.HytaleApiClient;
import net.modtale.launcher.hytale.HytaleAuthSession;
import net.modtale.launcher.hytale.HytaleVersion;
import net.modtale.launcher.i18n.LauncherI18n;
import net.modtale.launcher.i18n.LauncherI18n.LocaleOption;
import net.modtale.launcher.settings.LauncherConfig;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.ui.common.LauncherView;

public final class LauncherSettingsForm {

    private final TextField modsPathField = new TextField();
    private final TextField gameVersionField = new TextField();
    private final TextField hytaleGamePathField = new TextField();
    private final TextField hytaleUserDataPathField = new TextField();
    private final TextField hytaleJavaPathField = new TextField();
    private final TextField playHytaleGamePathField = new TextField();
    private final TextField playHytaleUserDataPathField = new TextField();
    private final TextField playHytaleJavaPathField = new TextField();
    private final ComboBox<String> hytaleBranchCombo = new ComboBox<>();
    private final ComboBox<HytaleVersion> hytaleVersionCombo = new ComboBox<>();
    private final ComboBox<LocaleOption> localeCombo = new ComboBox<>();
    private final CheckBox includeDependenciesCheck = new CheckBox();
    private final CheckBox includeOptionalCheck = new CheckBox();
    private final CheckBox autoUpdatesCheck = new CheckBox();
    private final CheckBox launcherAutoUpdatesCheck = new CheckBox();

    public LauncherSettingsForm() {
        LauncherI18n i18n = LauncherI18n.get();
        localeCombo.setItems(FXCollections.observableArrayList(i18n.supportedLocales()));
        i18n.bind(includeDependenciesCheck, "settings.dependencies.required");
        i18n.bind(includeOptionalCheck, "settings.dependencies.optional");
        i18n.bind(autoUpdatesCheck, "settings.updates.autoCheck");
        i18n.bind(launcherAutoUpdatesCheck, "settings.launcherUpdates.auto");
        hytaleBranchCombo.setItems(FXCollections.observableArrayList("release", "pre-release"));
        hytaleBranchCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String patchline) {
                return hytalePatchlineLabel(patchline);
            }

            @Override
            public String fromString(String value) {
                return HytaleApiClient.normalizeBranch(value);
            }
        });
        hytaleBranchCombo.setOnAction(event -> hytaleVersionCombo.getItems().clear());
        styleInput(modsPathField, gameVersionField, hytaleGamePathField, hytaleUserDataPathField, hytaleJavaPathField,
                playHytaleGamePathField, playHytaleUserDataPathField, playHytaleJavaPathField);
        styleCombo(localeCombo, hytaleBranchCombo, hytaleVersionCombo);
        includeDependenciesCheck.getStyleClass().add("native-check");
        includeOptionalCheck.getStyleClass().add("native-check");
        autoUpdatesCheck.getStyleClass().add("native-check");
        launcherAutoUpdatesCheck.getStyleClass().add("native-check");
    }

    public TextField modsPathField() {
        return modsPathField;
    }

    public TextField gameVersionField() {
        return gameVersionField;
    }

    public TextField hytaleGamePathField() {
        return hytaleGamePathField;
    }

    public TextField hytaleUserDataPathField() {
        return hytaleUserDataPathField;
    }

    public TextField hytaleJavaPathField() {
        return hytaleJavaPathField;
    }

    public TextField playHytaleGamePathField() {
        return playHytaleGamePathField;
    }

    public TextField playHytaleUserDataPathField() {
        return playHytaleUserDataPathField;
    }

    public TextField playHytaleJavaPathField() {
        return playHytaleJavaPathField;
    }

    public ComboBox<String> hytaleBranchCombo() {
        return hytaleBranchCombo;
    }

    public ComboBox<HytaleVersion> hytaleVersionCombo() {
        return hytaleVersionCombo;
    }

    public ComboBox<LocaleOption> localeCombo() {
        return localeCombo;
    }

    public void setHytalePatchlines(List<String> patchlines, String selectedPatchline) {
        List<String> normalizedPatchlines = normalizePatchlines(patchlines);
        String selected = HytaleApiClient.normalizeBranch(selectedPatchline);
        hytaleBranchCombo.setItems(FXCollections.observableArrayList(normalizedPatchlines));
        hytaleBranchCombo.setValue(normalizedPatchlines.contains(selected) ? selected : "release");
    }

    public CheckBox includeDependenciesCheck() {
        return includeDependenciesCheck;
    }

    public CheckBox includeOptionalCheck() {
        return includeOptionalCheck;
    }

    public CheckBox autoUpdatesCheck() {
        return autoUpdatesCheck;
    }

    public CheckBox launcherAutoUpdatesCheck() {
        return launcherAutoUpdatesCheck;
    }

    public void applyTo(LauncherSettings settings, LauncherView currentView, ModtaleApiClient apiClient) {
        settings.setHytaleModsPath(modsPathField.getText());
        settings.setGameVersion(gameVersionField.getText());
        boolean playView = currentView == LauncherView.PLAY;
        settings.setHytaleGamePath(playView ? playHytaleGamePathField.getText() : hytaleGamePathField.getText());
        settings.setHytaleUserDataPath(playView ? playHytaleUserDataPathField.getText() : hytaleUserDataPathField.getText());
        settings.setHytaleJavaPath(playView ? playHytaleJavaPathField.getText() : hytaleJavaPathField.getText());
        settings.setHytaleBranch(hytaleBranchCombo.getValue());
        HytaleVersion selected = hytaleVersionCombo.getValue();
        if (selected != null) {
            settings.setHytaleBuild(selected.build());
        }
        settings.setIncludeDependencies(includeDependenciesCheck.isSelected());
        settings.setIncludeOptionalDependencies(includeOptionalCheck.isSelected());
        settings.setAutoCheckUpdates(autoUpdatesCheck.isSelected());
        settings.setLauncherAutoUpdates(launcherAutoUpdatesCheck.isSelected());
        LocaleOption selectedLocale = localeCombo.getValue();
        if (selectedLocale != null) {
            settings.setLocale(selectedLocale.locale().toLanguageTag());
            LauncherI18n.get().setLocale(selectedLocale.locale());
        }
        apiClient.configure(LauncherConfig.apiBaseUrl());
    }

    public void reloadFrom(LauncherSettings settings) {
        modsPathField.setText(settings.getHytaleModsPath());
        gameVersionField.setText(settings.getGameVersion());
        hytaleGamePathField.setText(settings.getHytaleGamePath());
        hytaleUserDataPathField.setText(settings.getHytaleUserDataPath());
        hytaleJavaPathField.setText(settings.getHytaleJavaPath());
        playHytaleGamePathField.setText(settings.getHytaleGamePath());
        playHytaleUserDataPathField.setText(settings.getHytaleUserDataPath());
        playHytaleJavaPathField.setText(settings.getHytaleJavaPath());
        hytaleBranchCombo.setValue(settings.getHytaleBranch());
        includeDependenciesCheck.setSelected(settings.isIncludeDependencies());
        includeOptionalCheck.setSelected(settings.isIncludeOptionalDependencies());
        autoUpdatesCheck.setSelected(settings.isAutoCheckUpdates());
        launcherAutoUpdatesCheck.setSelected(settings.isLauncherAutoUpdates());
        localeCombo.getItems().stream()
                .filter(option -> option.locale().getLanguage().equals(LauncherI18n.normalize(settings.getLocale()).getLanguage()))
                .findFirst()
                .ifPresentOrElse(localeCombo::setValue, () -> localeCombo.getSelectionModel().selectFirst());
        selectConfiguredHytaleBuild(settings);
    }

    public static String hytalePatchlineLabel(String patchline) {
        LauncherI18n i18n = LauncherI18n.get();
        return switch (HytaleApiClient.normalizeBranch(patchline)) {
            case "pre-release" -> i18n.text("patchline.preRelease");
            case "release" -> i18n.text("patchline.release");
            default -> i18n.text("patchline.previous", HytaleApiClient.normalizeBranch(patchline));
        };
    }

    private static List<String> normalizePatchlines(List<String> patchlines) {
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        normalized.add("release");
        if (patchlines != null) {
            patchlines.stream()
                    .map(HytaleApiClient::normalizePatchlineId)
                    .flatMap(java.util.Optional::stream)
                    .forEach(normalized::add);
        }
        return List.copyOf(normalized);
    }

    public void selectConfiguredHytaleBuild(LauncherSettings settings) {
        int configuredBuild = settings.getHytaleBuild();
        if (configuredBuild <= 0 && !hytaleVersionCombo.getItems().isEmpty()) {
            hytaleVersionCombo.getSelectionModel().selectFirst();
            return;
        }
        for (HytaleVersion version : hytaleVersionCombo.getItems()) {
            if (version.build() == configuredBuild) {
                hytaleVersionCombo.setValue(version);
                return;
            }
        }
        if (!hytaleVersionCombo.getItems().isEmpty()) {
            hytaleVersionCombo.getSelectionModel().selectFirst();
        }
    }

    public void syncMetrics(
            LauncherSettings settings,
            Label hytaleStatus,
            Label hytaleMetric,
            Label buildMetric,
            Label gameMetric,
            Label modsMetric
    ) {
        LauncherI18n i18n = LauncherI18n.get();
        gameMetric.setText(value(settings.getGameVersion(), i18n.text("value.unset")));
        modsMetric.setText(i18n.plural("library.installed", settings.getInstalledProjects().size()));
        HytaleAuthSession session = settings.getHytaleAuthSession();
        hytaleStatus.setText(session == null ? i18n.text("hytale.signedOut") : i18n.text("hytale.connected", session));
        hytaleMetric.setText(session == null ? i18n.text("value.signedOut") : value(session.getUsername(), i18n.text("value.connected")));
        buildMetric.setText(settings.getHytaleBuild() > 0 ? "#" + settings.getHytaleBuild() : i18n.text("value.unset"));
    }
}
