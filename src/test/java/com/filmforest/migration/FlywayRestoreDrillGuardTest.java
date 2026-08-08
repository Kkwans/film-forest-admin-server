package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayRestoreDrillGuardTest {

    @Test
    void acceptsExplicitLocalRestoreTarget() {
        assertThatNoException().isThrownBy(() -> FlywayRestoreDrillIT.validateTarget(
                "isolated-restore-only",
                "RESTORED",
                "jdbc:mysql://127.0.0.1:43306/film_forest_phase0_restore",
                "drill_user",
                "drill_password"
        ));
    }

    @Test
    void acceptsExplicitLocalEmptyTarget() {
        assertThatNoException().isThrownBy(() -> FlywayRestoreDrillIT.validateTarget(
                "isolated-restore-only",
                "empty",
                "jdbc:mysql://localhost:53306/film_forest_phase0_empty?useSSL=false",
                "drill_user",
                "drill_password"
        ));
    }

    @Test
    void rejectsProductionPort() {
        assertThatThrownBy(() -> FlywayRestoreDrillIT.validateTarget(
                "isolated-restore-only",
                "RESTORED",
                "jdbc:mysql://127.0.0.1:3306/film_forest_phase0_restore",
                "drill_user",
                "drill_password"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonLocalHost() {
        assertThatThrownBy(() -> FlywayRestoreDrillIT.validateTarget(
                "isolated-restore-only",
                "RESTORED",
                "jdbc:mysql://192.0.2.10:43306/film_forest_phase0_restore",
                "drill_user",
                "drill_password"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsModeAndSchemaMismatch() {
        assertThatThrownBy(() -> FlywayRestoreDrillIT.validateTarget(
                "isolated-restore-only",
                "EMPTY",
                "jdbc:mysql://127.0.0.1:43306/film_forest_phase0_restore",
                "drill_user",
                "drill_password"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingConfirmation() {
        assertThatThrownBy(() -> FlywayRestoreDrillIT.validateTarget(
                "continue",
                "RESTORED",
                "jdbc:mysql://127.0.0.1:43306/film_forest_phase0_restore",
                "drill_user",
                "drill_password"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
