package software.spool.provisioning.acceptance.plugins;

import software.spool.infrastructure.provisioning.PieceProvisioning;
import software.spool.infrastructure.provisioning.ResourceAccess;
import software.spool.infrastructure.provisioning.TerraformFragment;
import software.spool.infrastructure.spi.SpoolPlugin;
import software.spool.infrastructure.spi.provider.PluginConfiguration;
import software.spool.infrastructure.spi.provider.provisioning.ProvisioningProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A store that exists only in the tests: one resource per piece, and a host every piece of its type shares. */
@SpoolPlugin(ProvisioningProvider.class)
public class TestStoreProvisioningProvider implements ProvisioningProvider {
    @Override
    public String name() {
        return "TEST_STORE";
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
        return target -> Optional.of(TerraformFragment.builder()
                .resource("test_store_host", "shared", Map.of("size", "small"))
                .resource("test_store", piece, Map.of("name", configuration.require("name")))
                .access(new ResourceAccess(List.of("${test_store." + piece + ".arn}"), List.of("store:Use")))
                .build());
    }
}
