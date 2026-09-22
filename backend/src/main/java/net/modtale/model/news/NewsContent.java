package net.modtale.model.news;

import jakarta.validation.constraints.*;
import java.util.List;

public record NewsContent(
    @NotBlank @Size(max=180) String title,
    @Size(max=500) String description,
    @Size(max=700) String excerpt,
    @NotBlank @Size(max=100) String author,
    @NotNull @Size(max=12) List<@NotBlank @Size(max=50) String> tags,
    @Size(max=2000) String heroImage,
    @Size(max=300) String heroAlt,
    @NotNull @Size(max=500000) String body
) {}
