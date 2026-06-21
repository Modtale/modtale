package net.modtale.model.project;

import java.util.List;

public final class ProjectLicenseSupport {
    public static final List<String> OPEN_SOURCE_LICENSES = List.of(
            "MIT",
            "Apache-2.0",
            "GPL-3.0",
            "LGPL-3.0",
            "AGPL-3.0",
            "MPL-2.0",
            "BSD-2-Clause",
            "BSD-3-Clause",
            "ISC",
            "Zlib",
            "Unlicense",
            "CC0-1.0",
            "CC-BY-4.0",
            "CC-BY-SA-4.0"
    );

    public static final List<String> SELECTABLE_LICENSES = List.of(
            "ARR",
            "MIT",
            "Apache-2.0",
            "GPL-3.0",
            "LGPL-3.0",
            "AGPL-3.0",
            "MPL-2.0",
            "CC0-1.0",
            "CC-BY-4.0",
            "CC-BY-SA-4.0",
            "CC-BY-NC-SA-4.0",
            "Unlicense"
    );

    private ProjectLicenseSupport() {
    }

    public static boolean isCustomLicense(String license) {
        return license != null && !license.isBlank() && !SELECTABLE_LICENSES.contains(license);
    }

    public static boolean shouldPersistCustomOpenSource(String license, boolean requestedOpenSource) {
        return requestedOpenSource && isCustomLicense(license);
    }
}
