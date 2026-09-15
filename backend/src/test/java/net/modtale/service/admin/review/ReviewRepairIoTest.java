package net.modtale.service.admin.review;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewRepairIoTest {
    @Test void remainingBudgetIsAppliedAndNeverBecomesUnlimited() {
        MongoCollection<Document> collection=mock(MongoCollection.class,RETURNS_SELF);var remaining=new AtomicLong(TimeUnit.SECONDS.toNanos(30));
        try(var io=ReviewRepairIo.open(remaining::get)) {
            ReviewRepairIo.collection(collection);verify(collection).withTimeout(5000,TimeUnit.MILLISECONDS);
            remaining.set(1);ReviewRepairIo.collection(collection);verify(collection).withTimeout(1,TimeUnit.MILLISECONDS);
            remaining.set(0);assertThrows(IllegalStateException.class,()->ReviewRepairIo.collection(collection));
        }
        clearInvocations(collection);assertSame(collection,ReviewRepairIo.collection(collection));verifyNoInteractions(collection);
    }
    @Test void cleanupHasIndependentBudgetAndRestoresExpiredScope() {
        MongoCollection<Document> collection=mock(MongoCollection.class,RETURNS_SELF);
        try(var expired=ReviewRepairIo.open(()->0)) {
            try(var cleanup=ReviewRepairIo.cleanup()) {assertSame(collection,ReviewRepairIo.collection(collection));}
            assertThrows(IllegalStateException.class,()->ReviewRepairIo.collection(collection));
        }
    }
    @Test void scopeDoesNotEscapeToAnotherThread()throws Exception {
        MongoCollection<Document> collection=mock(MongoCollection.class);
        try(var io=ReviewRepairIo.open(()->0);var worker=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            assertSame(collection,worker.submit(()->ReviewRepairIo.collection(collection)).get(5,TimeUnit.SECONDS));verifyNoInteractions(collection);
        }
    }
    @Test void nestedWorkCannotExtendItsParentDeadline() {
        MongoCollection<Document> collection=mock(MongoCollection.class,RETURNS_SELF);
        var parent=new AtomicLong(TimeUnit.MILLISECONDS.toNanos(75));
        try(var outer=ReviewRepairIo.open(parent::get);var inner=ReviewRepairIo.open(()->TimeUnit.SECONDS.toNanos(30))) {
            ReviewRepairIo.collection(collection);verify(collection).withTimeout(75,TimeUnit.MILLISECONDS);
            parent.set(1);ReviewRepairIo.collection(collection);verify(collection).withTimeout(1,TimeUnit.MILLISECONDS);
            assertEquals(1,inner.remainingNanos());
            parent.set(0);assertEquals(0,inner.remainingNanos());assertThrows(IllegalStateException.class,()->ReviewRepairIo.collection(collection));
            assertThrows(IllegalStateException.class,ReviewRepairIo::sessionOptions);
            assertThrows(IllegalStateException.class,()->ReviewRepairIo.transactionOptions(com.mongodb.TransactionOptions.builder().build()));
        }
    }
    @Test void shorterChildBudgetExpiresWithoutChangingParent() {
        MongoCollection<Document> collection=mock(MongoCollection.class,RETURNS_SELF);var child=new AtomicLong(TimeUnit.MILLISECONDS.toNanos(20));
        try(var parent=ReviewRepairIo.open(()->TimeUnit.SECONDS.toNanos(30))) {
            try(var inner=ReviewRepairIo.open(child::get)) {
                ReviewRepairIo.collection(collection);verify(collection).withTimeout(20,TimeUnit.MILLISECONDS);
                child.set(0);assertThrows(IllegalStateException.class,()->ReviewRepairIo.collection(collection));
            }
            ReviewRepairIo.collection(collection);verify(collection).withTimeout(5000,TimeUnit.MILLISECONDS);
        }
    }
    @Test void nestedOrdinaryScopeInsideCleanupRemainsBoundedByCleanup() {
        MongoCollection<Document> collection=mock(MongoCollection.class,RETURNS_SELF);
        try(var parent=ReviewRepairIo.open(()->0);var cleanup=ReviewRepairIo.cleanup();var inner=ReviewRepairIo.open(()->1)) {
            ReviewRepairIo.collection(collection);verify(collection).withTimeout(1,TimeUnit.MILLISECONDS);
        }
    }

}
