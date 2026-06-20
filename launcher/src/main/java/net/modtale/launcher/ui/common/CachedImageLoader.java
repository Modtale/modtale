package net.modtale.launcher.ui.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import net.modtale.launcher.cache.LauncherCachePaths;

public final class CachedImageLoader {

    private static final String IMAGE_KEY_PROPERTY = CachedImageLoader.class.getName() + ".imageKey";
    private static final int MAX_MEMORY_IMAGES = 384;

    private final Function<String, String> assetResolver;
    private final Executor executor;
    private final HttpClient httpClient;
    private final Path cacheDirectory;
    private final ConcurrentMap<String, CompletableFuture<Path>> downloads = new ConcurrentHashMap<>();
    private final Map<ImageKey, Image> memoryImages = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ImageKey, Image> eldest) {
            return size() > MAX_MEMORY_IMAGES;
        }
    });

    public CachedImageLoader(Function<String, String> assetResolver, Executor executor) {
        this(assetResolver, executor, LauncherCachePaths.cacheDirectory("images"));
    }

    public CachedImageLoader(Function<String, String> assetResolver, Executor executor, Path cacheDirectory) {
        this.assetResolver = assetResolver;
        this.executor = executor;
        this.cacheDirectory = cacheDirectory;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void loadInto(ImageView view, String rawUrl, double requestedWidth, double requestedHeight) {
        loadInto(view, rawUrl, requestedWidth, requestedHeight, false);
    }

    public void loadInto(ImageView view, String rawUrl, double requestedWidth, double requestedHeight, boolean preserveRatio) {
        String resolvedUrl = assetResolver.apply(rawUrl);
        ImageKey key = new ImageKey(resolvedUrl, requestedWidth, requestedHeight, preserveRatio);
        view.getProperties().put(IMAGE_KEY_PROPERTY, key);

        Image memoryImage = memoryImages.get(key);
        if (memoryImage != null) {
            view.setImage(memoryImage);
            return;
        }

        if (!isHttpUrl(resolvedUrl)) {
            setImage(view, key, imageFor(key, resolvedUrl));
            return;
        }

        Path cachedFile = cacheFile(resolvedUrl);
        if (Files.isRegularFile(cachedFile)) {
            setImage(view, key, imageFor(key, cachedFile.toUri().toString()));
            return;
        }

        downloads.computeIfAbsent(resolvedUrl, this::downloadAsync)
                .whenComplete((path, error) -> {
                    if (error != null || path == null) {
                        return;
                    }
                    Platform.runLater(() -> {
                        if (Objects.equals(view.getProperties().get(IMAGE_KEY_PROPERTY), key)) {
                            setImage(view, key, imageFor(key, path.toUri().toString()));
                        }
                    });
                });
    }

    public void clearMemory() {
        memoryImages.clear();
        downloads.clear();
    }

    public void clear(ImageView view) {
        view.getProperties().remove(IMAGE_KEY_PROPERTY);
        view.setImage(null);
    }

    private CompletableFuture<Path> downloadAsync(String resolvedUrl) {
        return CompletableFuture.supplyAsync(() -> download(resolvedUrl), executor)
                .whenComplete((path, error) -> downloads.remove(resolvedUrl));
    }

    private Path download(String resolvedUrl) {
        URI uri = URI.create(resolvedUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(45))
                .header("Accept", "image/png,image/jpeg,image/gif,image/bmp,image/*;q=0.8,*/*;q=0.5")
                .header("User-Agent", "ModtaleLauncher/0.1")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Image request returned HTTP " + response.statusCode());
            }

            Files.createDirectories(cacheDirectory);
            Path destination = cacheFile(resolvedUrl);
            Path temporary = Files.createTempFile(cacheDirectory, "image-", ".tmp");
            try (InputStream body = response.body()) {
                Files.copy(body, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return destination;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not cache image " + resolvedUrl, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Image download was interrupted.", ex);
        }
    }

    private void setImage(ImageView view, ImageKey key, Image image) {
        if (!Objects.equals(view.getProperties().get(IMAGE_KEY_PROPERTY), key)) {
            return;
        }
        view.setImage(image);
    }

    private Image imageFor(ImageKey key, String imageUrl) {
        Image cached = memoryImages.get(key);
        if (cached != null) {
            return cached;
        }
        Image image = new Image(imageUrl, key.requestedWidth(), key.requestedHeight(), key.preserveRatio(), true, true);
        memoryImages.put(key, image);
        return image;
    }

    private Path cacheFile(String resolvedUrl) {
        return cacheDirectory.resolve(sha256(resolvedUrl) + imageExtension(resolvedUrl));
    }

    private static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private static String imageExtension(String resolvedUrl) {
        String path = URI.create(resolvedUrl).getPath();
        String normalized = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
        for (String extension : java.util.List.of(".png", ".jpg", ".jpeg", ".gif", ".bmp")) {
            if (normalized.endsWith(extension)) {
                return extension;
            }
        }
        return ".img";
    }

    private record ImageKey(String url, double requestedWidth, double requestedHeight, boolean preserveRatio) {
    }
}
