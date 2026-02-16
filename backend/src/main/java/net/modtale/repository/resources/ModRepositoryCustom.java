package net.modtale.repository.resources;

import net.modtale.model.resources.Mod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ModRepositoryCustom {
    Page<Mod> searchMods(
            String search,
            List<String> tags,
            String gameVersion,
            String classification,
            Double minRating,
            Integer minDownloads,
            Integer minFavorites,
            Pageable pageable,
            String currentUsername,
            String sortBy,
            String viewCategory,
            LocalDate dateCutoff,
            String author
    );

    Page<Mod> findFavorites(List<String> modIds, String search, Pageable pageable);

    Page<Mod> searchDeletedMods(String search, Pageable pageable);
}