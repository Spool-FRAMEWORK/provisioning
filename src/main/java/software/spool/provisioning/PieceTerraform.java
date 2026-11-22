package software.spool.provisioning;

import software.spool.dsl.descriptors.infrastructure.InfrastructureComponentDescriptor;
import software.spool.infrastructure.PluginResolver;
import software.spool.infrastructure.provisioning.ProvisioningTarget;
import software.spool.infrastructure.provisioning.TerraformFragment;
import software.spool.infrastructure.spi.provider.PluginConfiguration;
import software.spool.infrastructure.spi.provider.provisioning.ProvisioningProvider;

import java.util.Map;
import java.util.Optional;

/** Asks the plugin of the type of a piece for its Terraform in a target. */
final class PieceTerraform {
    private PieceTerraform() {}

    static TerraformFragment of(InfrastructureComponentDescriptor piece, ProvisioningTarget target) {
        ProvisioningProvider provider = Optional.ofNullable(PluginResolver.available(ProvisioningProvider.class).get(piece.type()))
                .orElseThrow(() -> new ProvisioningException(piece.path(),
                        "type " + piece.type() + " has no plugin that generates its Terraform"));
        PluginConfiguration configuration = PluginConfiguration.of(piece.configuration(),
                Map.of(ProvisioningProvider.PIECE, piece.name()));
        return provider.create(configuration).forTarget(target)
                .orElseThrow(() -> new ProvisioningException(piece.path(),
                        "type " + piece.type() + " cannot be generated for the target " + target.label()));
    }
}
