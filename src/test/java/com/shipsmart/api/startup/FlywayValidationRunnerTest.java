package com.shipsmart.api.startup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;

/**
 * Guards the boot-time Flyway validator, including the fix that lets boot proceed when Flyway is
 * disabled (no bean) instead of crashing with a missing-dependency error.
 */
class FlywayValidationRunnerTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Flyway> provider(Flyway flyway) {
        ObjectProvider<Flyway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(flyway);
        return provider;
    }

    @Test
    void skipsValidationWhenFlywayDisabled() {
        var runner = new FlywayValidationRunner().flywayValidator(provider(null));
        assertThatCode(() -> runner.run(mock(ApplicationArguments.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void passesWhenNoPendingMigrations() {
        var info = mock(MigrationInfoService.class);
        when(info.pending()).thenReturn(new MigrationInfo[0]);
        when(info.applied()).thenReturn(new MigrationInfo[0]);
        when(info.all()).thenReturn(new MigrationInfo[0]);
        var flyway = mock(Flyway.class);
        when(flyway.info()).thenReturn(info);

        var runner = new FlywayValidationRunner().flywayValidator(provider(flyway));
        assertThatCode(() -> runner.run(mock(ApplicationArguments.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void failsFastWhenMigrationsPending() {
        var info = mock(MigrationInfoService.class);
        when(info.pending()).thenReturn(new MigrationInfo[] {mock(MigrationInfo.class)});
        var flyway = mock(Flyway.class);
        when(flyway.info()).thenReturn(info);

        var runner = new FlywayValidationRunner().flywayValidator(provider(flyway));
        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class);
    }
}
