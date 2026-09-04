package net.modtale.status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.Document;
import org.junit.jupiter.api.Test;

class MongoStatusStoreTest {

    private static final long THIRTY_DAYS = 30L * 24 * 60 * 60;

    @Test
    void recognizesTheLegacyNonTtlTimestampIndexForReplacement() {
        Document legacy = new Document("name", "timestamp")
                .append("key", new Document("timestamp", 1));

        assertTrue(MongoStatusStore.isTimestampIndex(legacy));
        assertFalse(MongoStatusStore.isMatchingTimestampTtlIndex(legacy, THIRTY_DAYS));
    }

    @Test
    void acceptsAnEquivalentTimestampTtlIndexRegardlessOfNumericBsonType() {
        Document ttl = new Document("name", "timestamp")
                .append("key", new Document("timestamp", 1L))
                .append("expireAfterSeconds", Integer.valueOf((int) THIRTY_DAYS));

        assertTrue(MongoStatusStore.isMatchingTimestampTtlIndex(ttl, THIRTY_DAYS));
    }

    @Test
    void doesNotMistakeCompoundOrDescendingIndexesForTheRetentionIndex() {
        Document compound = new Document("key", new Document("timestamp", 1).append("overallStatus", 1));
        Document descending = new Document("key", new Document("timestamp", -1));

        assertFalse(MongoStatusStore.isTimestampIndex(compound));
        assertFalse(MongoStatusStore.isTimestampIndex(descending));
    }
}
