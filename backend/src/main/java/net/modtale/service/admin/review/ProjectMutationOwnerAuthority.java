package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.model.Collation;
import net.modtale.model.project.*;
import net.modtale.model.user.*;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.account.AccountService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.context.request.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Internal authority for server-validated owner operations; never grants review approval or cancellation. */
public final class ProjectMutationOwnerAuthority {
    public record Bound(String actor,BooleanSupplier permitted) {}
    private static final Set<ApiKey.ApiPermission> PERMISSIONS=Set.of(ApiKey.ApiPermission.VERSION_CREATE,ApiKey.ApiPermission.VERSION_EDIT,
            ApiKey.ApiPermission.VERSION_DELETE,ApiKey.ApiPermission.PROJECT_STATUS_SUBMIT);
    private final AccountService accounts;private final AccessControlService access;private final ApiKeyRepository keys;private final MongoTemplate mongo;
    public ProjectMutationOwnerAuthority(AccountService accounts,AccessControlService access,ApiKeyRepository keys,MongoTemplate mongo) {
        this.accounts=Objects.requireNonNull(accounts);this.access=Objects.requireNonNull(access);this.keys=Objects.requireNonNull(keys);this.mongo=Objects.requireNonNull(mongo);
    }
    public String actor() {
        var user=current(SecurityContextHolder.getContext().getAuthentication());if(user==null)throw denied();return user.getId();
    }
    public Bound bind(Object projectId,Set<ApiKey.ApiPermission> requested) {
        if(!(projectId instanceof ObjectId || projectId instanceof String s && !s.isBlank() && s.length()<=128)
                || requested==null || requested.isEmpty() || !PERMISSIONS.containsAll(requested))throw denied();
        var permissions=Set.copyOf(requested);var authentication=SecurityContextHolder.getContext().getAuthentication();
        var user=current(authentication);if(user==null)throw denied();String actor=user.getId();
        boolean api=access.isApiKey(authentication);ApiKey original=api?requestKey(actor):null;
        String keyId=original==null?null:original.getId(),hash=original==null?null:original.getKeyHash();
        BooleanSupplier allowed=()->{
            if(SecurityContextHolder.getContext().getAuthentication()!=authentication)return false;
            var refreshed=current(authentication);if(refreshed==null || !actor.equals(refreshed.getId()))return false;
            var raw=ReviewRepairIo.collection(mongo.getCollection("projects").withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY))
                    .find(new Document("_id",projectId)).collation(Collation.builder().locale("simple").build()).maxTime(5,TimeUnit.SECONDS).first();
            if(raw==null || !projectId.equals(raw.get("_id")))return false;
            var project=mongo.getConverter().read(Project.class,raw);
            if(project.getStatus()==null || !(Set.of(ProjectStatus.DRAFT,ProjectStatus.PUBLISHED,ProjectStatus.UNLISTED,ProjectStatus.PRIVATE).contains(project.getStatus())
                    || project.getStatus()==ProjectStatus.PENDING && permissions.contains(ApiKey.ApiPermission.PROJECT_STATUS_SUBMIT)))return false;
            var key=api?keys.findById(keyId).orElse(null):null;
            if(api && (key==null || !actor.equals(key.getUserId()) || !hash.equals(key.getKeyHash())))return false;
            for(var permission:permissions) {
                if(!access.hasCurrentProjectMembershipPermission(project,refreshed,permission))return false;
                if(api && !scoped(key,project,actor,permission))return false;
            }
            return SecurityContextHolder.getContext().getAuthentication()==authentication && authentication.isAuthenticated();
        };
        if(!allowed.getAsBoolean())throw denied();return new Bound(actor,allowed);
    }
    private User current(Authentication authentication) {
        if(authentication==null || !authentication.isAuthenticated())return null;
        var user=accounts.getCurrentUser(authentication);return user==null || user.isDeleted() || user.getId()==null || user.getId().isBlank()?null:user;
    }
    private ApiKey requestKey(String actor) {
        if(!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))throw denied();
        String supplied=attributes.getRequest().getHeader("X-MODTALE-KEY");if(supplied==null || supplied.length()>256)throw denied();
        supplied=supplied.trim();for(String prefix:List.of("Bearer ","ApiKey "))if(supplied.regionMatches(true,0,prefix,0,prefix.length())){supplied=supplied.substring(prefix.length()).trim();break;}
        if(supplied.length()<10)throw denied();var key=keys.findByPrefix(supplied.substring(0,10)).orElse(null);
        if(key==null || key.getId()==null || !actor.equals(key.getUserId()) || key.getKeyHash()==null
                || !new BCryptPasswordEncoder().matches(supplied,key.getKeyHash()))throw denied();return key;
    }
    private static boolean scoped(ApiKey key,Project project,String actor,ApiKey.ApiPermission permission) {
        var contexts=key.getContextPermissions();if(contexts==null)return false;
        if(contexts.getOrDefault(project.getId(),Set.of()).contains(permission))return true;
        if(actor.equals(project.getAuthorId()) && contexts.getOrDefault("PERSONAL",Set.of()).contains(permission))return true;
        return project.getAuthorId()!=null && contexts.getOrDefault(project.getAuthorId(),Set.of()).contains(permission);
    }
    private static SecurityException denied(){return new SecurityException("Project mutation is not permitted");}
}
