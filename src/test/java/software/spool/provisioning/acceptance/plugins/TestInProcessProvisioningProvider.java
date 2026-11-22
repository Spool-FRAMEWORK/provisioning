package software.spool.provisioning.acceptance.plugins;

import software.spool.infrastructure.provisioning.PieceProvisioning;
import software.spool.infrastructure.provisioning.TerraformFragment;
import software.spool.infrastructure.spi.SpoolPlugin;
import software.spool.infrastructure.spi.provider.PluginConfiguration;
import software.spool.infrastructure.spi.provider.provisioning.ProvisioningProvider;

import java.util.Optional;

/** A piece that exists only in the tests and lives in the process: nothing to create, in any target. */
@SpoolPlugin(ProvisioningProvider.class)
public class TestInProcessProvisioningProvider implements ProvisioningProvider {
    @Override
    public String name() {
        return "TEST_IN_PROCESS";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(PluginConfiguration configuration) {
        return true;
    }

    @Override
    public PieceProvisioning create(PluginConfiguration configuration) {
        return target -> Optional.of(TerraformFragment.empty());
    }
}
