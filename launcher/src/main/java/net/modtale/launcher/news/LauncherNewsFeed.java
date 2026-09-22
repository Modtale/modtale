package net.modtale.launcher.news;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class LauncherNewsFeed {
    public record Result(List<LauncherNewsPost> posts, List<String> failedSources) {}
    private record SourceResult(List<LauncherNewsPost> posts, String failedSource) {}

    public static CompletableFuture<Result> load(Supplier<List<LauncherNewsPost>> modtale,
            Supplier<List<LauncherNewsPost>> hytale, Executor executor) {
        return fetch("Modtale", modtale, executor).thenCombine(fetch("Hytale", hytale, executor), (a, b) -> {
            var unique = new LinkedHashMap<String, LauncherNewsPost>();
            Stream.concat(a.posts.stream(), b.posts.stream())
                    .sorted(Comparator.comparing(LauncherNewsPost::publishedAt).reversed())
                    .forEach(post -> unique.putIfAbsent(post.url(), post));
            return new Result(List.copyOf(unique.values()), Stream.of(a.failedSource, b.failedSource)
                    .filter(source -> !source.isEmpty()).toList());
        });
    }
    private static CompletableFuture<SourceResult> fetch(String name, Supplier<List<LauncherNewsPost>> fetch, Executor executor) {
        return CompletableFuture.supplyAsync(fetch, executor).handle((posts, error) ->
                error == null ? new SourceResult(posts == null ? List.of() : posts, "") : new SourceResult(List.of(), name));
    }
    private LauncherNewsFeed() {}
}
