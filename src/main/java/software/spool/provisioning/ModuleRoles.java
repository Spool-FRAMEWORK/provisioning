package software.spool.provisioning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.spool.dsl.descriptors.DeclaredModule;
import software.spool.dsl.descriptors.SpoolNodeDescriptor;
import software.spool.dsl.descriptors.infrastructure.InfrastructureComponentDescriptor;
import software.spool.dsl.descriptors.infrastructure.InfrastructureDescriptor;
import software.spool.dsl.registry.ModuleProviderRegistry;
import software.spool.infrastructure.provisioning.ResourceAccess;
import software.spool.infrastructure.provisioning.TerraformFragment;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A role in AWS for every module, reaching only the pieces the module uses: the ones of the kinds its provider
 * declares, as the module resolves them (named in {@code uses}, or the only one of their kind).
 */
final class ModuleRoles {
    static final String ROLE_SERVICE = "ecs-tasks.amazonaws.com";
    private static final String POLICY_VERSION = "2012-10-17";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ModuleRoles() {}

    static void add(SpoolNodeDescriptor node, Map<String, TerraformFragment> fragmentsByPiece, TerraformDocument document) {
        for (DeclaredModule module : node.declaredModules()) {
            List<ResourceAccess> accesses = accessesOf(node, module, fragmentsByPiece);
            document.add(module.path(), roleOf(TerraformNames.of(module.descriptor().id()), accesses));
        }
    }

    private static List<ResourceAccess> accessesOf(SpoolNodeDescriptor node, DeclaredModule module,
                                                   Map<String, TerraformFragment> fragmentsByPiece) {
        InfrastructureDescriptor infrastructure = node.infrastructureOf(module);
        return ModuleProviderRegistry.uses(module.descriptor()).stream()
                .map(infrastructure::find)
                .flatMap(Optional::stream)
                .map(InfrastructureComponentDescriptor::name)
                .flatMap(piece -> fragmentsByPiece.get(piece).accesses().stream())
                .toList();
    }

    private static TerraformFragment roleOf(String name, List<ResourceAccess> accesses) {
        TerraformFragment.Builder role = TerraformFragment.builder()
                .requiredProvider("aws", "hashicorp/aws", "~> 5.0")
                .variable("module_role_service", Map.of("type", "string", "default", ROLE_SERVICE))
                .resource("aws_iam_role", name, Map.of(
                        "name", "spool-" + name,
                        "assume_role_policy", json(Map.of("Version", POLICY_VERSION, "Statement", List.of(Map.of(
                                "Effect", "Allow",
                                "Principal", Map.of("Service", "${var.module_role_service}"),
                                "Action", "sts:AssumeRole"))))));
        if (!accesses.isEmpty()) {
            role.resource("aws_iam_role_policy", name, Map.of(
                    "name", "spool-" + name,
                    "role", "${aws_iam_role." + name + ".id}",
                    "policy", json(Map.of("Version", POLICY_VERSION, "Statement", accesses.stream()
                            .map(access -> Map.of("Effect", "Allow", "Action", access.actions(), "Resource", access.resources()))
                            .toList()))));
        }
        return role.build();
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
