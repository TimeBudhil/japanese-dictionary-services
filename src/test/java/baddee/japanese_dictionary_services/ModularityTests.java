package baddee.japanese_dictionary_services;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build when a module reaches into another module's internals (D9). */
class ModularityTests {

	@Test
	void verifiesModuleStructure() {
		ApplicationModules.of(JapaneseDictionaryServicesApplication.class).verify();
	}

}
