package net.modtale.service.admin.review;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static net.modtale.service.admin.review.VersionReviewTransition.Change.*;

class VersionReviewTransitionTest {
    Document original(){return new Document("_id","v").append("gameVersions",List.of("a","b")).append("scanResult",new Document("remoteReview","malformed-but-retained"));}
    VersionReviewTransition.Transition compare(Document before,Document after){return VersionReviewTransition.classify(List.of(before),List.of(after)).getFirst();}
    @ParameterizedTest @ValueSource(strings={"changelog","channel","incompatibleProjectIds","downloadCount"})
    void metadataDoesNotRequestAnotherReview(String field){var old=original();var next=new Document(old).append(field,"changed");var result=compare(old,next);assertEquals(Set.of(METADATA),result.changes());assertFalse(result.requiresRetention());}
    @Test void runtimeOrderAloneDoesNotInvalidateReview(){var old=original();var next=new Document(old).append("gameVersions",List.of("b","a"));assertTrue(compare(old,next).changes().isEmpty());assertNotEquals(compare(old,next).beforeSha256(),compare(old,next).afterSha256());}
    @Test void runtimeMultiplicityAndMalformedTypesAreNotNormalizedAway(){var old=original();assertEquals(Set.of(CONTEXT),compare(old,new Document(old).append("gameVersions",List.of("a","a","b"))).changes());assertEquals(Set.of(CONTEXT),compare(old,new Document(old).append("gameVersions",List.of(1))).changes());}
    @Test void contextAndQueueReplacementAreBothRetained(){var old=original();var next=new Document(old).append("dependencies",List.of(new Document("projectId","dep"))).append("scanResult",new Document("scanState","QUEUED"));var result=compare(old,next);assertEquals(Set.of(CONTEXT,REVIEW),result.changes());assertTrue(result.requiresRetention());}
    @Test void prunedRemoteHistoryAndSecurityHoldsStillRequireRetention(){for(String field:List.of("retainedRemoteReview","reviewReplacement","reviewIsolation","replacementSecurityHold","approvedSecurityEvidence","findingReviewHead")){var old=new Document("_id","v").append(field,"malformed");assertTrue(compare(old,new Document(old).append("hash","new")).requiresRetention(),field);}}
    @Test void unknownChangesAndNumericTypeChangesAreConservative(){var old=original().append("futureSecurityField",1);var result=compare(old,new Document(old).append("futureSecurityField",1L));assertEquals(Set.of(UNKNOWN),result.changes());assertTrue(result.requiresRetention());}
    @Test void reorderingMatchesVersionIdsAndDoesNotCreateReviews(){var a=original();var b=new Document("_id","b");var results=VersionReviewTransition.classify(List.of(a,b),List.of(b,a));assertEquals(1,results.getFirst().afterIndex());assertTrue(results.stream().allMatch(r->r.changes().isEmpty()));}
    @Test void removalsAndNewUploadsHaveSeparateIdentities(){var results=VersionReviewTransition.classify(List.of(original()),List.of(new Document("_id","new").append("versionNumber","same-label")));assertEquals(Set.of(REMOVED),results.getFirst().changes());assertTrue(results.getFirst().requiresRetention());assertEquals(Set.of(ADDED),results.get(1).changes());assertFalse(results.get(1).priorHistory());}
    @Test void draftWithoutHistoryDoesNotRequireRetention(){var old=new Document("_id","v").append("reviewStatus","PENDING").append("securityApprovedAt",0L);assertFalse(compare(old,new Document(old).append("scanResult",new Document("scanState","QUEUED"))).requiresRetention());}
    @Test void duplicateAndMalformedIdsAreRejected(){var old=original();assertThrows(IllegalArgumentException.class,()->VersionReviewTransition.classify(List.of(old,old),List.of()));assertThrows(IllegalArgumentException.class,()->VersionReviewTransition.classify(List.of(),List.of(new Document("_id",1))));}
    @Test void returnedClassificationCannotBeMutatedWithItsInput(){var old=original();var next=new Document(old).append("hash","new");var result=compare(old,next);var hash=result.afterSha256();next.put("hash","other");assertEquals(hash,result.afterSha256());assertThrows(UnsupportedOperationException.class,()->result.changes().clear());}
    @Test void missingApprovalEvidenceIsNotTreatedAsANewDraft(){var old=new Document("_id","v").append("reviewStatus","APPROVED");assertTrue(compare(old,new Document(old).append("scanResult",new Document("scanState","QUEUED"))).requiresRetention());}
    @Test void unknownNullFieldRemovalCannotEscapeClassification(){var old=new Document("_id","v").append("unknown",null);var result=compare(old,new Document("_id","v"));assertEquals(Set.of(UNKNOWN),result.changes());assertTrue(result.requiresRetention());}

    @Test void dependencyOrderIsIgnoredButDuplicatesAndRawTypesRemainSignificant(){
        var a=new Document("projectId","a").append("versionNumber","1");var b=new Document("projectId","b").append("versionNumber","2");
        var old=original().append("dependencies",List.of(a,b));assertTrue(compare(old,new Document(old).append("dependencies",List.of(b,a))).changes().isEmpty());
        assertEquals(Set.of(CONTEXT),compare(old,new Document(old).append("dependencies",List.of(a,a,b))).changes());
        assertEquals(Set.of(CONTEXT),compare(old,new Document(old).append("dependencies",List.of(new Document(a).append("versionNumber",1),b))).changes());
    }

}
