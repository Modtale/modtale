package net.modtale.service.auth;

import java.util.*;
import net.modtale.model.user.ApiKey;

final class ApiKeyContextSnapshot {
    private ApiKeyContextSnapshot() {}
    static Map<String,Set<ApiKey.ApiPermission>> copy(Map<String,Set<ApiKey.ApiPermission>> source) {
        var result=new LinkedHashMap<String,Set<ApiKey.ApiPermission>>();
        source.forEach((context,permissions)->result.put(context,Collections.unmodifiableSet(new LinkedHashSet<>(permissions))));
        return Collections.unmodifiableMap(result);
    }
}
