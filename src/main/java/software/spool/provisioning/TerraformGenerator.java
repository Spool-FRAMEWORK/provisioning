package software.spool.provisioning;

import software.spool.dsl.DescriptorLoader;
import software.spool.dsl.descriptors.SpoolNodeDescriptor;
import software.spool.dsl.descriptors.infrastructure.InfrastructureComponentDescriptor;
import software.spool.dsl.source.ClasspathDescriptorSource;
import software.spool.dsl.source.DescriptorSource;
import software.spool.dsl.source.FileSystemDescriptorSource;
import software.spool.infrastructure.provisioning.ProvisioningTarget;
import software.spool.infrastructure.provisioning.TerraformFragment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Generates the Terraform of the infrastructure of a descriptor for a target. The descriptor is loaded like a node
 * does, with includes and profile, except that every value of the environment becomes a Terraform variable. Every
 * piece asks the plugin of its type for its part; in AWS every module also gets its role. Nothing is applied.
 */
public final class TerraformGenerator {
    private static final Map<String, Object> STRING_VARIABLE = Map.of("type", "string");

    private final DescriptorSource source;

    public TerraformGenerator(DescriptorSource source) {
        this.source = source;
    }

    public static TerraformGenerator fromFileSystem() {
        return new TerraformGenerator(new FileSystemDescriptorSource());
    }

    public static TerraformGenerator fromClasspath() {
        return new TerraformGenerator(new ClasspathDescriptorSource());
    }

    public GeneratedTerraform generate(Path descriptor, Optional<String> profile, ProvisioningTarget target)
            throws IOException {
        return generate(FileSystemDescriptorSource.locationOf(descriptor), profile, target);
    }

    /**
     * @throws IOException when the descriptor cannot be loaded
     * @throws ProvisioningException when a piece cannot be generated for the target
     */
    public GeneratedTerraform generate(String location, Optional<String> profile, ProvisioningTarget target)
            throws IOException {
        EnvironmentAsVariables environment = new EnvironmentAsVariables();
        SpoolNodeDescriptor node = new DescriptorLoader(source, environment).load(location, profile, Function.identity());
        TerraformDocument document = new TerraformDocument();
        Map<String, TerraformFragment> fragmentsByPiece = new LinkedHashMap<>();
        for (InfrastructureComponentDescriptor piece : node.infrastructure().pieces()) {
            TerraformFragment fragment = PieceTerraform.of(piece, target);
            fragmentsByPiece.put(piece.name(), fragment);
            document.add(piece.path(), fragment);
        }
        environment.names().forEach(name -> document.variable(name, STRING_VARIABLE));
        if (target == ProvisioningTarget.AWS) ModuleRoles.add(node, fragmentsByPiece, document);
        return document.build();
    }
}
