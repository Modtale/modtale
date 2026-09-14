package net.modtale.controller.admin;

import java.util.List;
import net.modtale.model.dto.admin.AdminVerificationQueueItemDTO;
import net.modtale.service.admin.review.ModerationQueueCursor;
import net.modtale.service.admin.review.ModerationQueuePageReader;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/verification/queue")
public class ModerationQueuePageController {
    public record Page(List<AdminVerificationQueueItemDTO> items,String nextCursor,int unavailableItems,String order) {}
    private final ModerationQueuePageReader reader;
    public ModerationQueuePageController(ModerationQueuePageReader reader) {this.reader=reader;}
    @GetMapping("/page")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication)")
    public ResponseEntity<Page> read(@RequestParam(required=false) String cursor,@RequestParam(defaultValue="25") int limit) {
        ModerationQueuePageReader.Cursor position;
        try {
            if(limit<1 || limit>50)throw new IllegalArgumentException();
            position=ModerationQueueCursor.decode(cursor);
        } catch(IllegalArgumentException invalid) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid queue pagination");}
        var page=reader.page(position,limit);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Page(page.items(),ModerationQueueCursor.encode(page.next()),page.unavailableItems(),"PROJECT_VERSION"));
    }
}
