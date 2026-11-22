package software.spool.provisioning.acceptance.plugins;

import software.spool.infrastructure.provisioning.PieceProvisioning;
import software.spool.infrastructure.provisioning.ProvisioningTarget;
import software.spool.infrastructure.provisioning.TerraformFragment;
import software.spool.infrastructure.spi.SpoolPlugin;
import software.spool.infrastructure.spi.provider.PluginConfiguration;
import software.spool.infrastructure.spi.provider.provisioning.ProvisioningProvider;

import java.util.Map;
import java.util.Optional;

/** A piece that exists only in the tests and can only be created locally. */
@SpoolPlugin(ProvisioningProvider.class)
public class TestLocalOnlyProvisioningProvider implements ProvisioningProvider {
    @Override
    public String name() {
        return "TEST_LOCAL_ONLY";
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
        String piece = configuration.require(PIECE, String.class);
        return target -> target == ProvisioningTarget.LOCAL
                ? Optional.of(TerraformFragment.builder()
                        .resource("test_local_only", piece, Map.of("name", configuration.require("name")))
                        .build())
                : Optional.empty();
    }
}
