package net.modtale.repository.finance;

import net.modtale.model.finance.PlatformFinanceSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlatformFinanceSettingsRepository extends MongoRepository<PlatformFinanceSettings, String> {
}
