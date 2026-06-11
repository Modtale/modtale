package net.modtale.util;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoIdUtilsTest {

    @Test
    void expandIdsIncludesStringAndObjectIdFormsForLegacyMongoIds() {
        String legacyId = "507f1f77bcf86cd799439011";

        List<Object> expanded = MongoIdUtils.expandIds(List.of(legacyId, "user-uuid", "", " "));

        assertEquals(3, expanded.size());
        assertEquals(legacyId, expanded.get(0));
        assertInstanceOf(ObjectId.class, expanded.get(1));
        assertEquals("user-uuid", expanded.get(2));
        assertTrue(expanded.contains(new ObjectId(legacyId)));
    }
}
