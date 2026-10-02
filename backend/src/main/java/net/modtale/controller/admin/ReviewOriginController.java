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
@RequestMapping("/api/v1/admin/verification/origins")
public class ReviewOriginController {
    public record Position(String projectIdType,String projectId,int versionIndex) {}
    public record Item(Position position,String versionId,boolean ambiguousVersion,String requestId,String jobId,ReviewOriginInventory.OriginState originState) {}
    public record Page(List<Item> items,String nextCursor,int examinedSlots,String scope) {}
    private final ReviewOriginInventory inventory;
    public ReviewOriginController(ReviewOriginInventory inventory){this.inventory=inventory;}
    @GetMapping("/page")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication)")
    public ResponseEntity<Page> read(@RequestParam(required=false) String cursor,@RequestParam(defaultValue="25") int limit) {
        RemoteReviewDiscovery.Cursor position;
        try {
            if(limit<1 || limit>64)throw new IllegalArgumentException();
            position=ReviewOriginCursor.decode(cursor);
        }catch(IllegalArgumentException invalid){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid origin pagination");}
        ReviewOriginInventory.Page page;
        try {page=inventory.page(position,limit);}
        catch(RuntimeException unavailable){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Review origin inventory unavailable");}
        var items=page.items().stream().map(item->{
            boolean oid=item.projectId() instanceof ObjectId;
            return new Item(new Position(oid?"OBJECT_ID":"STRING",oid?((ObjectId)item.projectId()).toHexString():(String)item.projectId(),item.versionIndex()),
                    item.versionId(),item.ambiguousVersion(),item.requestId(),item.jobId(),item.originState());
        }).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Page(items,ReviewOriginCursor.encode(page.next()),page.examined(),"RETAINED_REVIEW_ORIGINS"));
    }
}
