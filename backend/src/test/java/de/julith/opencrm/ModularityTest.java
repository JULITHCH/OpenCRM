package de.julith.opencrm;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Verstöße gegen den Modulschnitt sind ein Build-Fehler (E-64). */
class ModularityTest {

    @Test
    void verifyModuleStructure() {
        ApplicationModules.of(OpenCrmApplication.class).verify();
    }
}
