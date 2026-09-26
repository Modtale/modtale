package net.modtale.controller.admin;

import java.util.List;
import net.modtale.service.admin.review.*;
import net.modtale.service.security.scan.RemoteReviewDiscovery;
import org.bson.types.ObjectId;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/verification/diagnostics")
public class ReviewStateDiagnosticController {
    public record Position(String projectIdType,String projectId,int versionIndex) {}
    public record Item(Position position,String versionId,List<ReviewStateDiagnosticReader.Reason> reasons) {}
    public record Page(List<Item> items,String nextCursor,int examinedSlots,String scope) {}
    private final ReviewStateDiagnosticReader reader;
    public ReviewStateDiagnosticController(ReviewStateDiagnosticReader reader){this.reader=reader;}
    @GetMapping("/page")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication)")
    public ResponseEntity<Page> read(@RequestParam(required=false) String cursor,@RequestParam(defaultValue="25") int limit) {
        RemoteReviewDiscovery.Cursor position;
        try {
            if(limit<1 || limit>64)throw new IllegalArgumentException();
            position=ReviewStateDiagnosticCursor.decode(cursor);
        } catch(IllegalArgumentException invalid) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid diagnostic pagination");}
        var page=reader.page(position,limit);
        var items=page.items().stream().map(item->{
            Object id=item.projectId();boolean objectId=id instanceof ObjectId;
            return new Item(new Position(objectId?"OBJECT_ID":"STRING",objectId?((ObjectId)id).toHexString():(String)id,item.versionIndex()),item.versionId(),item.reasons());
        }).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Page(items,ReviewStateDiagnosticCursor.encode(page.next()),page.examined(),"PENDING_SCAN_STRUCTURE"));
    }
}
