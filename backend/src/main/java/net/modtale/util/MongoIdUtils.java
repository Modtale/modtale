package net.modtale.util;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MongoIdUtils {

    private MongoIdUtils() {}

    public static List<Object> expandIds(Collection<String> ids) {
        Set<Object> expanded = new LinkedHashSet<>();
        if (ids == null) {
            return List.of();
        }

        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }

            expanded.add(id);
            if (ObjectId.isValid(id)) {
                expanded.add(new ObjectId(id));
            }
        }

        return new ArrayList<>(expanded);
    }
}
