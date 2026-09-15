package net.modtale.controller.admin;

import net.modtale.service.admin.review.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/verification/cancellations")
@ConditionalOnProperty(name="app.warden.repair.cancellation.enabled",havingValue="true")
@PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication) and @apiSecurity.hasAdminPermission('PROJECT_VERSION_RESCAN', authentication)")
public class ReviewCancellationController {
    public record Prepare(String isolationId) {}
    private final ReviewCancellationAccess access;
    public ReviewCancellationController(ReviewCancellationAccess access){this.access=access;}
    @GetMapping("/capabilities") public ResponseEntity<ReviewCancellationAccess.Capability> capabilities(){return response(access.capability());}
    @GetMapping("/targets/{id}") public ResponseEntity<ReviewOrphanCancellationJournal.Preview> preview(@PathVariable String id){return response(access.preview(id));}
    @GetMapping("/operations/{id}") public ResponseEntity<ReviewOrphanCancellationJournal.Receipt> recover(@PathVariable String id){return response(access.recover(id));}
    @PostMapping("/prepare") public ResponseEntity<ReviewOrphanCancellationJournal.Prepared> prepare(@RequestBody Prepare request){return response(access.prepare(request==null?null:request.isolationId()));}
    @PostMapping("/execute") public ResponseEntity<ReviewOrphanCancellationExecutor.Execution> execute(@RequestBody ReviewOrphanCancellationJournal.Prepared request){return response(access.execute(request));}
    @PostMapping("/receipt") public ResponseEntity<ReviewOrphanCancellationJournal.Receipt> receipt(@RequestBody ReviewOrphanCancellationJournal.Prepared request){return response(access.receipt(request));}
    @PostMapping("/checks") public ResponseEntity<ReviewCancellationReconciler.Receipt> check(@RequestBody ReviewCancellationAccess.Check request){return response(access.check(request));}
    @PostMapping("/checks/receipt") public ResponseEntity<ReviewCancellationReconciler.Receipt> checkReceipt(@RequestBody ReviewCancellationAccess.Check request){return response(access.checkReceipt(request));}
    private static <T> ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @ExceptionHandler(SecurityException.class) ResponseEntity<Void> forbidden(){return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore()).build();}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) ResponseEntity<Void> invalid(){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();}
    @ExceptionHandler(IllegalStateException.class) ResponseEntity<Void> conflict(){return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).build();}
    @ExceptionHandler(com.mongodb.MongoException.class) ResponseEntity<Void> unavailable(){return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore()).build();}
}
