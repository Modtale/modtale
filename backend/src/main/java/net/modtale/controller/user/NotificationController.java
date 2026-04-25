package net.modtale.controller.user;

import net.modtale.model.user.User;
import net.modtale.service.user.NotificationService;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired private NotificationService notificationService;
    @Autowired private UserService userService;

    @GetMapping
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_READ', authentication)")
    public ResponseEntity<?> getUserNotifications() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(notificationService.getUserNotifications(user.getId()));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_UPDATE', authentication)")
    public ResponseEntity<?> markAsRead(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unread")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_UPDATE', authentication)")
    public ResponseEntity<?> markAsUnread(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        notificationService.markAsUnread(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_UPDATE', authentication)")
    public ResponseEntity<?> markAllAsRead() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_DELETE', authentication)")
    public ResponseEntity<?> deleteNotification(@PathVariable String id) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        notificationService.deleteNotification(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clear-all")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_DELETE', authentication)")
    public ResponseEntity<?> clearAll() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        notificationService.clearAll(user.getId());
        return ResponseEntity.ok().build();
    }
}