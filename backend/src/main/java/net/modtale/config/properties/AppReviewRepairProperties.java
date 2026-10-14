package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.*;

@ConfigurationProperties(prefix="app.warden.repair")
public record AppReviewRepairProperties(@DefaultValue("false") boolean enabled,@DefaultValue("") String activeKey,
        Map<String,String> signingKeys,@DefaultValue("1") int concurrency,@DefaultValue("300000") long preparationLifetimeMillis) {
    public AppReviewRepairProperties {
        signingKeys=signingKeys==null?Map.of():Map.copyOf(signingKeys);
        if(enabled) {
            if(concurrency<1 || concurrency>2 || preparationLifetimeMillis<1000 || preparationLifetimeMillis>900000
                    || activeKey==null || !signingKeys.containsKey(activeKey) || signingKeys.isEmpty() || signingKeys.size()>8)throw invalid();
            decode(signingKeys);
        }
    }
    public Map<String,byte[]> decodedKeys(){if(!enabled)throw invalid();return decode(signingKeys);}
    private static Map<String,byte[]> decode(Map<String,String> keys) {
        var decoded=new HashMap<String,byte[]>();
        keys.forEach((id,value)->{
            try {
                if(id==null || !id.matches("[A-Za-z0-9_-]{1,64}") || value==null || value.length()>172)throw invalid();
                byte[] key=Base64.getDecoder().decode(value);
                if(key.length<32 || key.length>128 || !Base64.getEncoder().encodeToString(key).equals(value))throw invalid();
                decoded.put(id,key);
            } catch(IllegalArgumentException invalid){throw invalid();}
        });return Map.copyOf(decoded);
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid review repair settings");}
    @Override public String toString(){return "ReviewRepairSettings[enabled="+enabled+"]";}
}
