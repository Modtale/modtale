package net.modtale.controller;

import net.modtale.model.dto.UserDTO;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.model.user.BannedEmail;
import net.modtale.model.resources.Mod;
import net.modtale.model.resources.ModVersion;
import net.modtale.model.AdminLog;
import net.modtale.service.user.UserService;
import net.modtale.service.resources.ModService;
import net.modtale.service.resources.StorageService;
import net.modtale.repository.user.UserRepository;
import net.modtale.repository.AdminLogRepository;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired private UserService userService;
    @Autowired private ModService modService;
    @Autowired private UserRepository userRepository;
    @Autowired private StorageService storageService;
    @Autowired private AdminLogRepository adminLogRepository;

    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    private boolean isSuperAdmin(User user) {
        return user != null && SUPER_ADMIN_ID.equals(user.getId());
    }

    private boolean isAdmin(User user) {
        return (user != null && user.getRoles() != null && user.getRoles().contains("ADMIN")) || isSuperAdmin(user);
    }

    private User getSafeUser() {
        try {
            return userService.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    private void logAction(String adminId, String action, String targetId, String targetType, String details) {
        adminLogRepository.save(new AdminLog(adminId, action, targetId, targetType, details));
    }

    @GetMapping("/users/bans")
    public ResponseEntity<List<BannedEmail>> getBannedEmails() {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userService.getBannedEmails());
    }

    @PostMapping("/users/bans")
    public ResponseEntity<?> banEmail(@RequestBody Map<String, String> body) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String email = body.get("email");
        String reason = body.get("reason");
        try {
            userService.banEmail(email, reason, currentUser.getId());
            logAction(currentUser.getId(), "BAN_EMAIL", email, "EMAIL", "Reason: " + reason);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/users/bans")
    public ResponseEntity<?> unbanEmail(@RequestParam String email) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userService.unbanEmail(email);
        logAction(currentUser.getId(), "UNBAN_EMAIL", email, "EMAIL", null);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{username}")
    public ResponseEntity<?> getUserDetails(@PathVariable String username) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<User> target = userRepository.findByUsernameIgnoreCase(username);
        if (target.isEmpty()) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(UserDTO.fromEntity(target.get(), true));
    }

    @DeleteMapping("/users/{username}")
    public ResponseEntity<?> deleteUser(@PathVariable String username) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User target = userRepository.findByUsername(username).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        try {
            userService.deleteUser(target.getId());
            logAction(currentUser.getId(), "DELETE_USER", target.getId(), "USER", "Username: " + username);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/users/{username}/tier")
    public ResponseEntity<?> setUserTier(@PathVariable String username, @RequestParam String tier) {
        User currentUser = getSafeUser();
        if (!isSuperAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied", "message", "You do not have permission."));
        }

        try {
            ApiKey.Tier tierEnum;
            if ("USER".equalsIgnoreCase(tier) || "FREE".equalsIgnoreCase(tier)) {
                tierEnum = ApiKey.Tier.USER;
            } else {
                tierEnum = ApiKey.Tier.valueOf(tier.toUpperCase());
            }

            userService.setUserTier(username, tierEnum);
            logAction(currentUser.getId(), "UPDATE_TIER", username, "USER", "New Tier: " + tierEnum);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "User " + username + " updated to tier " + tierEnum
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Tier", "message", "Tier must be USER or ENTERPRISE"));
        }
    }

    @PostMapping("/users/{username}/role")
    public ResponseEntity<?> addUserRole(@PathVariable String username, @RequestParam String role) {
        User currentUser = getSafeUser();
        if (!isSuperAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Super Admin can manage roles.");
        }

        User target = userRepository.findByUsername(username).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        if (target.getRoles() == null) target.setRoles(new ArrayList<>());
        if (!target.getRoles().contains(role)) {
            target.getRoles().add(role);
        }
        userRepository.save(target);
        logAction(currentUser.getId(), "ADD_ROLE", target.getId(), "USER", "Role: " + role);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{username}/role")
    public ResponseEntity<?> removeUserRole(@PathVariable String username, @RequestParam String role) {
        User currentUser = getSafeUser();
        if (!isSuperAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Super Admin can manage roles.");
        }

        User target = userRepository.findByUsername(username).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        if (target.getRoles() != null) {
            target.getRoles().remove(role);
            userRepository.save(target);
        }
        logAction(currentUser.getId(), "REMOVE_ROLE", target.getId(), "USER", "Role: " + role);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/verification/queue")
    public ResponseEntity<List<Mod>> getVerificationQueue() {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(modService.getVerificationQueue());
    }

    @GetMapping("/projects/{id}/review-details")
    public ResponseEntity<?> getProjectReviewDetails(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Mod mod = modService.getRawModById(id);
        if (mod == null) return ResponseEntity.notFound().build();

        User author = userRepository.findByUsername(mod.getAuthor()).orElse(null);
        if (author == null) {
            author = userRepository.findById(mod.getAuthor()).orElse(null);
        }

        Map<String, Object> authorStats = Map.of(
                "accountAge", author != null ? author.getCreatedAt() : "Unknown",
                "tier", author != null ? author.getTier() : "Unknown",
                "avatarUrl", author != null ? (author.getAvatarUrl() != null ? author.getAvatarUrl() : "") : "",
                "totalProjects", author != null ? modService.getCreatorProjects(author.getId(), Pageable.unpaged()).getTotalElements() : 0
        );

        return ResponseEntity.ok(Map.of(
                "mod", mod,
                "authorStats", authorStats
        ));
    }

    @PostMapping("/projects/{id}/publish")
    public ResponseEntity<?> publishProject(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            modService.publishMod(id, currentUser.getId());
            logAction(currentUser.getId(), "PUBLISH_PROJECT", id, "PROJECT", null);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/versions/{versionId}/approve")
    public ResponseEntity<?> approveVersion(@PathVariable String id, @PathVariable String versionId) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            modService.approveVersion(id, versionId);
            logAction(currentUser.getId(), "APPROVE_VERSION", id, "VERSION", "VerID: " + versionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/versions/{versionId}/reject")
    public ResponseEntity<?> rejectVersion(@PathVariable String id, @PathVariable String versionId, @RequestBody Map<String, String> body) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            modService.rejectVersion(id, versionId, body.get("reason"));
            logAction(currentUser.getId(), "REJECT_VERSION", id, "VERSION", "VerID: " + versionId + ", Reason: " + body.get("reason"));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/reject")
    public ResponseEntity<?> rejectProject(@PathVariable String id, @RequestBody Map<String, String> body) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            modService.rejectMod(id, body.get("reason"));
            logAction(currentUser.getId(), "REJECT_PROJECT", id, "PROJECT", "Reason: " + body.get("reason"));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            modService.adminDeleteProject(id);
            logAction(currentUser.getId(), "DELETE_PROJECT", id, "PROJECT", null);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/restore")
    public ResponseEntity<?> restoreProject(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            modService.adminRestoreProject(id);
            logAction(currentUser.getId(), "RESTORE_PROJECT", id, "PROJECT", null);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/unlist")
    public ResponseEntity<?> unlistProject(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            modService.adminUnlistProject(id);
            logAction(currentUser.getId(), "UNLIST_PROJECT", id, "PROJECT", null);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/projects/{id}/versions/{versionId}")
    public ResponseEntity<?> deleteProjectVersion(@PathVariable String id, @PathVariable String versionId) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            modService.adminDeleteVersion(id, versionId);
            logAction(currentUser.getId(), "DELETE_VERSION", id, "VERSION", "VerID: " + versionId);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/projects/search")
    public ResponseEntity<?> searchProjects(@RequestParam String query, @RequestParam(required = false, defaultValue = "false") boolean deleted) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        if (deleted) {
            return ResponseEntity.ok(modService.searchDeletedProjects(query, 0, 10).getContent());
        } else {
            return ResponseEntity.ok(modService.getMods(null, query, 0, 10, "relevance", null, null, null, null, null, null, null, null).getContent());
        }
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<?> getProjectById(@PathVariable String id) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Mod mod = modService.getAdminProjectDetails(id);
        if (mod == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(mod);
    }

    @GetMapping("/projects/{id}/versions/{version}/structure")
    public ResponseEntity<?> getJarStructure(@PathVariable String id, @PathVariable String version) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        try {
            Mod mod = modService.getRawModById(id);
            if (mod == null) return ResponseEntity.notFound().build();

            ModVersion targetVer = modService.findVersion(mod, version);

            if (targetVer == null) {
                targetVer = mod.getVersions().stream().filter(v -> v.getId().equals(version)).findFirst().orElse(null);
            }

            if (targetVer == null || targetVer.getFileUrl() == null) return ResponseEntity.notFound().build();

            byte[] jarBytes = storageService.download(targetVer.getFileUrl());
            if (jarBytes == null) return ResponseEntity.status(500).body("File download failed");

            List<String> files = new ArrayList<>();
            try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
                JarEntry entry;
                while ((entry = jis.getNextJarEntry()) != null) {
                    if (!entry.isDirectory()) {
                        files.add(entry.getName());
                    }
                }
            }

            files.sort((a, b) -> {
                boolean aIsClass = a.endsWith(".class");
                boolean bIsClass = b.endsWith(".class");
                if (aIsClass && !bIsClass) return 1;
                if (!aIsClass && bIsClass) return -1;
                return a.compareTo(b);
            });

            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to read JAR structure: " + e.getMessage());
        }
    }

    @GetMapping("/projects/{id}/versions/{version}/file")
    public ResponseEntity<String> readJarFile(
            @PathVariable String id,
            @PathVariable String version,
            @RequestParam String path
    ) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        try {
            Mod mod = modService.getRawModById(id);
            if (mod == null) return ResponseEntity.notFound().build();

            ModVersion targetVer = modService.findVersion(mod, version);

            if (targetVer == null) {
                targetVer = mod.getVersions().stream().filter(v -> v.getId().equals(version)).findFirst().orElse(null);
            }

            if (targetVer == null || targetVer.getFileUrl() == null) return ResponseEntity.notFound().build();

            byte[] jarBytes = storageService.download(targetVer.getFileUrl());
            if (jarBytes == null) return ResponseEntity.status(500).body("File download failed");

            byte[] fileBytes = null;

            try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
                JarEntry entry;
                while ((entry = jis.getNextJarEntry()) != null) {
                    if (entry.getName().equals(path)) {
                        fileBytes = jis.readAllBytes();
                        break;
                    }
                }
            }

            if (fileBytes == null) return ResponseEntity.notFound().build();

            if (path.endsWith(".class")) {
                return ResponseEntity.ok(decompileClass(fileBytes, path));
            } else if (isTextFile(path)) {
                return ResponseEntity.ok(new String(fileBytes, StandardCharsets.UTF_8));
            } else {
                return ResponseEntity.ok("[Binary File: " + fileBytes.length + " bytes]");
            }

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error reading file: " + e.getMessage());
        }
    }

    @PostMapping("/projects/{id}/versions/{versionId}/scan")
    public ResponseEntity<?> rescanVersion(@PathVariable String id, @PathVariable String versionId) {
        User currentUser = getSafeUser();
        if (!isAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            modService.triggerRescan(id, versionId);
            logAction(currentUser.getId(), "RESCAN_VERSION", id, "VERSION", "VerID: " + versionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private boolean isTextFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".yaml") ||
                lower.endsWith(".txt") || lower.endsWith(".xml") || lower.endsWith(".properties") ||
                lower.endsWith(".md") || lower.endsWith(".ini") || lower.endsWith(".cfg");
    }

    private String decompileClass(byte[] classBytes, String originalPath) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("decompile");
            String fileName = new File(originalPath).getName();
            fileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");

            Path classFile = tempDir.resolve(fileName);
            Files.write(classFile, classBytes);

            Map<String, Object> options = new HashMap<>();
            options.put("rbr", "0");
            options.put("rsy", "0");
            options.put("ind", "    ");
            options.put("lit", "1");
            final StringBuilder result = new StringBuilder();

            IResultSaver resultSaver = new IResultSaver() {
                @Override public void saveFolder(String path) {}
                @Override public void copyFile(String source, String path, String entryName) {}
                @Override public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) { result.append(content); }
                @Override public void createArchive(String path, String archiveName, java.util.jar.Manifest manifest) {}
                @Override public void saveDirEntry(String path, String archiveName, String entryName) {}
                @Override public void copyEntry(String source, String path, String archiveName, String entry) {}
                @Override public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) { result.append(content); }
                @Override public void closeArchive(String path, String archiveName) {}
            };

            IBytecodeProvider provider = (externalPath, internalPath) -> {
                try {
                    return Files.readAllBytes(new File(externalPath).toPath());
                } catch (IOException e) {
                    return new byte[0];
                }
            };

            Fernflower decompiler = new Fernflower(provider, resultSaver, options, new IFernflowerLogger() {
                @Override public void writeMessage(String message, Severity severity) {}
                @Override public void writeMessage(String message, Severity severity, Throwable t) {}
            });

            decompiler.addSource(classFile.toFile());
            decompiler.decompileContext();

            String output = result.toString();
            return output.isEmpty() ? "// Decompilation failed or produced no output." : output;

        } catch (Exception e) {
            return "// Error decompiling file: " + e.getMessage();
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException ignored) {}
            }
        }
    }
}