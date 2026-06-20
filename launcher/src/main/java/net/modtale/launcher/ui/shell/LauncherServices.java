package net.modtale.launcher.ui.shell;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javafx.scene.Node;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.cache.LauncherCacheService;
import net.modtale.launcher.discord.DiscordRichPresenceService;
import net.modtale.launcher.hytale.HytaleApiClient;
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.hytale.HytaleGameLauncher;
import net.modtale.launcher.install.ModInstaller;
import net.modtale.launcher.install.UpdateService;
import net.modtale.launcher.install.WorldModListInstaller;
import net.modtale.launcher.settings.LauncherConfig;
import net.modtale.launcher.settings.SettingsStore;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherAssetResolver;
import net.modtale.launcher.ui.common.LauncherScrollSupport;
import net.modtale.launcher.update.LauncherUpdateService;

public final class LauncherServices {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(4);
    private final SettingsStore settingsStore = new SettingsStore();
    private final ModtaleApiClient apiClient = new ModtaleApiClient(
            LauncherConfig.apiBaseUrl(),
            SettingsStore.defaultSessionPath()
    );
    private final ModInstaller installer = new ModInstaller(apiClient, settingsStore);
    private final WorldModListInstaller worldModListInstaller = new WorldModListInstaller(apiClient);
    private final UpdateService updateService = new UpdateService(apiClient);
    private final LauncherUpdateService launcherUpdateService = new LauncherUpdateService();
    private final DiscordRichPresenceService discordRichPresence = DiscordRichPresenceService.fromConfig();
    private final HytaleApiClient hytaleApiClient = new HytaleApiClient();
    private final HytaleAuthService hytaleAuthService = new HytaleAuthService(hytaleApiClient, settingsStore);
    private final HytaleGameLauncher hytaleGameLauncher = new HytaleGameLauncher(hytaleAuthService);
    private final LauncherCacheService cacheService = new LauncherCacheService();
    private final LauncherScrollSupport scrollSupport;
    private final LauncherAssetResolver assetResolver;
    private final CachedImageLoader accountImageLoader;
    private final CachedImageLoader projectPageImageLoader;
    private final ProjectCardFactory projectCardFactory;

    public LauncherServices(Supplier<Node> sceneRoot, Supplier<String> fallbackProjectAssetUrl) {
        scrollSupport = new LauncherScrollSupport(sceneRoot);
        assetResolver = new LauncherAssetResolver(apiClient, fallbackProjectAssetUrl);
        accountImageLoader = new CachedImageLoader(assetResolver::resolveBackendAsset, imageExecutor);
        projectPageImageLoader = new CachedImageLoader(assetResolver::resolve, imageExecutor);
        projectCardFactory = new ProjectCardFactory(assetResolver::resolve, imageExecutor);
    }

    public ExecutorService executor() {
        return executor;
    }

    public SettingsStore settingsStore() {
        return settingsStore;
    }

    public ModtaleApiClient apiClient() {
        return apiClient;
    }

    public ModInstaller installer() {
        return installer;
    }

    public WorldModListInstaller worldModListInstaller() {
        return worldModListInstaller;
    }

    public UpdateService updateService() {
        return updateService;
    }

    public LauncherUpdateService launcherUpdateService() {
        return launcherUpdateService;
    }

    public DiscordRichPresenceService discordRichPresence() {
        return discordRichPresence;
    }

    public HytaleAuthService hytaleAuthService() {
        return hytaleAuthService;
    }

    public HytaleGameLauncher hytaleGameLauncher() {
        return hytaleGameLauncher;
    }

    public LauncherScrollSupport scrollSupport() {
        return scrollSupport;
    }

    public ProjectCardFactory projectCardFactory() {
        return projectCardFactory;
    }

    public CachedImageLoader accountImageLoader() {
        return accountImageLoader;
    }

    public CachedImageLoader projectPageImageLoader() {
        return projectPageImageLoader;
    }

    public LauncherCacheService.ClearResult clearCaches() {
        try {
            LauncherCacheService.ClearResult result = cacheService.clear();
            apiClient.clearResponseCache();
            accountImageLoader.clearMemory();
            projectPageImageLoader.clearMemory();
            projectCardFactory.clearImageCache();
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not clear launcher cache.", ex);
        }
    }

    public void shutdown() {
        discordRichPresence.shutdown();
        executor.shutdownNow();
        imageExecutor.shutdownNow();
    }
}
