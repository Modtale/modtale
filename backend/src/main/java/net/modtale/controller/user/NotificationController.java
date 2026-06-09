package net.modtale.controller.user;

import net.modtale.exception.ErrorMessageUtils;
import net.modtale.mapper.UserResponseMapper;
import net.modtale.model.user.User;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired private NotificationService notificationService;
    @Autowired private AccountService accountService;

    @GetMapping
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_READ', authentication)")
    public ResponseEntity<?> getUserNotifications() {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before viewing notifications.");
        return ResponseEntity.ok(notificationService.getUserNotifications(user.getId()).stream()
                .map(UserResponseMapper::toNotificationDTO)
                .toList());
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_UPDATE', authentication)")
    public ResponseEntity<?> markAsRead(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before updating notifications.");
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unread")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_UPDATE', authentication)")
    public ResponseEntity<?> markAsUnread(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before updating notifications.");
        notificationService.markAsUnread(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_UPDATE', authentication)")
    public ResponseEntity<?> markAllAsRead() {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before updating notifications.");
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_DELETE', authentication)")
    public ResponseEntity<?> deleteNotification(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before deleting notifications.");
        notificationService.deleteNotification(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clear-all")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('NOTIFICATION_DELETE', authentication)")
    public ResponseEntity<?> clearAll() {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before clearing notifications.");
        notificationService.clearAll(user.getId());
        return ResponseEntity.ok().build();
    }
}
