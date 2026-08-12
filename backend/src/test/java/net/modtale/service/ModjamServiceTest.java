package net.modtale.service;

import net.modtale.model.jam.Modjam;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModjamServiceTest {

    private ModjamService service;
    private ModjamRepository modjamRepository;
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        service = new ModjamService();
        modjamRepository = mock(ModjamRepository.class);
        storageService = mock(StorageService.class);
        ReflectionTestUtils.setField(service, "modjamRepository", modjamRepository);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "userRepository", mock(UserRepository.class));
    }

    @Test
    void updateJamRejectsNonHostsBeforeApplyingChanges() {
        Modjam existing = jamOwnedBy("host-1");
        Modjam update = new Modjam();
        update.setSlug("updated-jam");
        when(modjamRepository.findById("jam-1")).thenReturn(Optional.of(existing));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updateJam("jam-1", update, "other-user")
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(modjamRepository, never()).save(any());
    }

    @Test
    void updateIconRejectsNonHostsBeforeUploading() {
        when(modjamRepository.findById("jam-1")).thenReturn(Optional.of(jamOwnedBy("host-1")));
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "image/png", new byte[]{1});

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updateIcon("jam-1", file, "other-user")
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(storageService, never()).upload(any(), any());
        verify(modjamRepository, never()).save(any());
    }

    @Test
    void updateBannerRejectsNonHostsBeforeUploading() {
        when(modjamRepository.findById("jam-1")).thenReturn(Optional.of(jamOwnedBy("host-1")));
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", new byte[]{1});

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updateBanner("jam-1", file, "other-user")
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(storageService, never()).upload(any(), any());
        verify(modjamRepository, never()).save(any());
    }

    private static Modjam jamOwnedBy(String hostId) {
        Modjam jam = new Modjam();
        jam.setId("jam-1");
        jam.setHostId(hostId);
        return jam;
    }
}
