package net.modtale.controller.admin;

import net.modtale.service.admin.review.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/verification/repairs")
@ConditionalOnProperty(name="app.warden.repair.enabled",havingValue="true")
@PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication) and @apiSecurity.hasAdminPermission('PROJECT_VERSION_RESCAN', authentication)")
public class ReviewRepairController {
    private final ReviewRepairAccess access;
    public ReviewRepairController(ReviewRepairAccess access){this.access=access;}
    @GetMapping("/capabilities") public ResponseEntity<ReviewRepairAccess.Capability> capabilities(){return response(access.capability());}
    @GetMapping("/operations") public ResponseEntity<ReviewRepairOperationReader.Page> operations(@RequestParam(required=false) String cursor,@RequestParam(defaultValue="25") int limit){return response(access.operations(cursor,limit));}
    @GetMapping("/operations/{id}") public ResponseEntity<ReviewRepairAccess.Recovered> recover(@PathVariable String id){return response(access.recover(id));}
    @PostMapping("/inspect") public ResponseEntity<ReviewRepairAccess.Preview> inspect(@RequestBody ReviewRepairAccess.Inspect request){return response(access.inspect(request));}
    @PostMapping("/prepare") public ResponseEntity<ReviewRepairPreparation.Prepared> prepare(@RequestBody ReviewRepairAccess.Prepare request){return response(access.prepare(request));}
    @PostMapping("/execute") public ResponseEntity<ReviewIsolationExecutor.Result> execute(@RequestBody ReviewRepairPreparation.Prepared request){return response(access.execute(request));}
    @PostMapping("/close-expired") public ResponseEntity<ReviewRepairAccess.Receipt> closeExpired(@RequestBody ReviewRepairPreparation.Prepared request){return response(access.closeExpired(request));}
    @PostMapping("/receipt") public ResponseEntity<ReviewRepairAccess.Receipt> receipt(@RequestBody ReviewRepairPreparation.Prepared request){return response(access.receipt(request));}
    private static <T> ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @ExceptionHandler(SecurityException.class) ResponseEntity<Void> forbidden(){return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore()).build();}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) ResponseEntity<Void> invalid(){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();}
    @ExceptionHandler(IllegalStateException.class) ResponseEntity<Void> conflict(){return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).build();}
    @ExceptionHandler(com.mongodb.MongoException.class) ResponseEntity<Void> unavailable(){return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore()).build();}
}
