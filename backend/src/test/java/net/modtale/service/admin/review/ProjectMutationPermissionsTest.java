package net.modtale.service.admin.review;

import net.modtale.model.user.ApiKey.ApiPermission;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectMutationPermissionsTest {
    Document root(){return new Document("_id","p").append("status","DRAFT").append("versions",List.of(new Document("_id","v"),new Document("_id","w")));}
    Set<ApiPermission> required(Document before,Document after,ProjectMutationPreparation.Mutation kind){return ProjectMutationPreparation.requiredPermissions(bytes(before),bytes(after),"p",kind);}
    static byte[] bytes(Document doc){var b=new RawBsonDocument(doc,new DocumentCodec()).getByteBuffer().asNIO();var data=new byte[b.remaining()];b.get(data);return data;}
    @Test void pureReorderingRequiresEditButShiftFromRemovalDoesNot(){
        var before=root();var after=root();after.put("versions",List.of(new Document("_id","w"),new Document("_id","v")));
        assertEquals(Set.of(ApiPermission.VERSION_EDIT),required(before,after,ProjectMutationPreparation.Mutation.VERSION_LIST));
        after.put("versions",List.of(new Document("_id","w")));assertEquals(Set.of(ApiPermission.VERSION_DELETE),required(before,after,ProjectMutationPreparation.Mutation.VERSION_LIST));
    }
    @Test void submissionDoesNotAuthorizeAdditionalContextChanges(){
        var before=root();var after=root();after.put("status","PENDING");
        assertEquals(Set.of(ApiPermission.PROJECT_STATUS_SUBMIT),required(before,after,ProjectMutationPreparation.Mutation.SUBMISSION));
        after.getList("versions",Document.class).getFirst().put("gameVersions",List.of("changed"));
        assertEquals(Set.of(ApiPermission.PROJECT_STATUS_SUBMIT,ApiPermission.VERSION_EDIT),required(before,after,ProjectMutationPreparation.Mutation.SUBMISSION));
    }
    @Test void rootAuthorityChangesAndNoOpCannotObtainMutationPermissions(){
        var before=root();var after=root();assertThrows(RuntimeException.class,()->required(before,after,ProjectMutationPreparation.Mutation.VERSION_LIST));
        after.put("authorId","other");assertThrows(RuntimeException.class,()->required(before,after,ProjectMutationPreparation.Mutation.VERSION_LIST));
    }
}
