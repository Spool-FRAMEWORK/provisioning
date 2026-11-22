package software.spool.provisioning;

import software.spool.infrastructure.provisioning.TerraformFragment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Joins the fragments of the pieces into one Terraform. A block two pieces give the same, like a container they
 * share, is written once; the same block given differently fails, naming both pieces.
 */
final class TerraformDocument {
    private final Map<String, Map<String, Owned>> resources = new LinkedHashMap<>();
    private final Map<String, Owned> requiredProviders = new LinkedHashMap<>();
    private final Map<String, Owned> providers = new LinkedHashMap<>();
    private final Map<String, Owned> variables = new LinkedHashMap<>();

    void add(String owner, TerraformFragment fragment) {
        fragment.resources().forEach((type, byName) -> byName.forEach((name, body) ->
                put(resources.computeIfAbsent(type, ignored -> new LinkedHashMap<>()), "resource " + type + "." + name,
                        name, owner, body)));
        fragment.requiredProviders().forEach((name, body) -> put(requiredProviders, "provider " + name, name, owner, body));
        fragment.providers().forEach((name, body) -> put(providers, "the configuration of provider " + name, name, owner, body));
        fragment.variables().forEach((name, body) -> put(variables, "variable " + name, name, owner, body));
    }

    void variable(String name, Map<String, Object> declaration) {
        put(variables, "variable " + name, name, "the descriptor", declaration);
    }

    GeneratedTerraform build() {
        Map<String, Object> main = new LinkedHashMap<>();
        if (!requiredProviders.isEmpty()) main.put("terraform", Map.of("required_providers", bodies(requiredProviders)));
        if (!providers.isEmpty()) main.put("provider", bodies(providers));
        Map<String, Object> resourceBlocks = new LinkedHashMap<>();
        resources.forEach((type, byName) -> resourceBlocks.put(type, bodies(byName)));
        if (!resourceBlocks.isEmpty()) main.put("resource", resourceBlocks);
        Map<String, Object> declarations = variables.isEmpty() ? Map.of() : Map.of("variable", bodies(variables));
        return new GeneratedTerraform(main, declarations);
    }

    private static void put(Map<String, Owned> blocks, String what, String name, String owner, Object body) {
        Owned existing = blocks.putIfAbsent(name, new Owned(owner, body));
        if (existing != null && !Objects.equals(existing.body(), body)) {
            throw new ProvisioningException(owner, "gives " + what + " differently from " + existing.owner());
        }
    }

    private static Map<String, Object> bodies(Map<String, Owned> blocks) {
        Map<String, Object> bodies = new LinkedHashMap<>();
        blocks.forEach((name, owned) -> bodies.put(name, owned.body()));
        return bodies;
    }

    private record Owned(String owner, Object body) {}
}
