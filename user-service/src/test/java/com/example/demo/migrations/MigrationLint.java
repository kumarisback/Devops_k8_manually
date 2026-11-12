package com.example.demo.migrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rules for db/migration (roadmap step 24, gitops docs/MIGRATIONS.md), checked
 * in CI before a migration can reach a database (MigrationLintTest).
 *
 * <ul>
 *   <li>Files are {@code V<n>__<words>.json}, numbered 1, 2, 3... without gaps
 *       or repeats, each one MongoDB command (a JSON object).</li>
 *   <li>Commands that remove or rename data, or can reject the running
 *       version's writes, belong to the contract phase of an expand-contract
 *       change, after every version that needs the old shape is gone: only in
 *       a migration named {@code V<n>__contract_<words>.json}.</li>
 *   <li>An index build can't run in a transaction on an existing collection:
 *       {@code createIndexes} needs {@code executeInTransaction=false} in its
 *       {@code <file>.conf}.</li>
 *   <li>A {@code .conf} belongs to a migration and holds only
 *       {@code executeInTransaction}.</li>
 * </ul>
 */
final class MigrationLint {

    private static final Pattern NAME = Pattern.compile("V(\\d+)__([a-z0-9]+(?:_[a-z0-9]+)*)\\.json");
    private static final Pattern CONF = Pattern.compile("(V\\d+__[a-z0-9_]+\\.json)\\.conf");
    private static final Set<String> DESTRUCTIVE_COMMANDS =
            Set.of("drop", "dropDatabase", "dropIndexes", "renameCollection", "delete", "collMod");
    private static final Set<String> DESTRUCTIVE_OPERATORS = Set.of("$unset", "$rename", "$unsetField");

    private final ObjectMapper json = new ObjectMapper();

    List<String> problems(Path dir) throws IOException {
        List<String> problems = new ArrayList<>();
        Map<Integer, String> versions = new TreeMap<>();
        Map<String, String> confs = new TreeMap<>();
        List<Path> files;
        try (Stream<Path> listing = Files.list(dir)) {
            files = listing.sorted().toList();
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            Matcher conf = CONF.matcher(name);
            if (conf.matches()) {
                confs.put(conf.group(1), Files.readString(file));
                continue;
            }
            Matcher migration = NAME.matcher(name);
            if (!migration.matches()) {
                problems.add(name + ": not V<n>__<lower_snake_case>.json");
                continue;
            }
            int version = Integer.parseInt(migration.group(1));
            String previous = versions.put(version, name);
            if (previous != null) {
                problems.add(name + ": version " + version + " also used by " + previous);
            }
            JsonNode command;
            try {
                command = json.readTree(file.toFile());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                problems.add(name + ": not valid JSON (" + e.getOriginalMessage() + ")");
                continue;
            }
            if (command == null || !command.isObject() || command.isEmpty()) {
                problems.add(name + ": must be one MongoDB command, a JSON object");
                continue;
            }
            String verb = command.fieldNames().next();
            boolean contract = migration.group(2).startsWith("contract_");
            if (!contract && DESTRUCTIVE_COMMANDS.contains(verb)) {
                problems.add(name + ": " + verb + " removes data or can refuse the running version's writes;"
                        + " only in a contract_ migration");
            }
            if (!contract && "update".equals(verb) && usesAny(command.get("updates"), DESTRUCTIVE_OPERATORS)) {
                problems.add(name + ": removes or renames fields; only in a contract_ migration");
            }
            if ("createIndexes".equals(verb)
                    && !confs.getOrDefault(name, readConf(dir, name)).contains("executeInTransaction=false")) {
                problems.add(name + ": createIndexes can't run in a transaction on an existing collection;"
                        + " add " + name + ".conf with executeInTransaction=false");
            }
        }
        int expected = 1;
        for (int version : versions.keySet()) {
            if (version != expected) {
                problems.add("V" + expected + ": missing (versions must be 1, 2, 3... without gaps)");
                expected = version;
            }
            expected++;
        }
        for (Map.Entry<String, String> conf : confs.entrySet()) {
            if (!versions.containsValue(conf.getKey())) {
                problems.add(conf.getKey() + ".conf: no such migration");
            }
            for (String line : conf.getValue().strip().split("\\R")) {
                if (!line.isBlank() && !line.strip().startsWith("executeInTransaction=")) {
                    problems.add(conf.getKey() + ".conf: only executeInTransaction is allowed, found " + line.strip());
                }
            }
        }
        return problems;
    }

    private static String readConf(Path dir, String migration) throws IOException {
        Path conf = dir.resolve(migration + ".conf");
        return Files.exists(conf) ? Files.readString(conf) : "";
    }

    private static boolean usesAny(JsonNode node, Set<String> operators) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (operators.contains(field.getKey()) || usesAny(field.getValue(), operators)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (usesAny(item, operators)) {
                    return true;
                }
            }
        }
        return false;
    }
}
