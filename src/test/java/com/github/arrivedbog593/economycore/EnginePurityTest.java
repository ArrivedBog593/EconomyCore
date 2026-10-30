package com.github.arrivedbog593.economycore;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the engine testable.
 * <p>
 * Everything under {@code engine} is plain Java, which is what lets these
 * tests run in seconds without starting the game. One import of Minecraft
 * there and that stops being true without anybody noticing until a test
 * fails to load, so this reads the source and says where.
 */
class EnginePurityTest {

    private static final Path ENGINE =
            Path.of("src/main/java/com/github/arrivedbog593/economycore/engine");

    private static final Pattern GAME_LIBRARIES =
            Pattern.compile("\\b(?:net\\.minecraft|net\\.neoforged|com\\.mojang)\\.[\\w.]+");

    @Test
    void theEngineIsPlainJava() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ENGINE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher matcher = GAME_LIBRARIES.matcher(lines.get(i));
                    while (matcher.find()) {
                        found.add(ENGINE.relativize(file) + ":" + (i + 1) + "  " + matcher.group());
                    }
                }
            }
        }
        assertTrue(found.isEmpty(),
                "engine is tested without the game, so it must not use it:\n  "
                        + String.join("\n  ", found));
    }

    @Test
    void theScanSeesTheSources() throws IOException {
        // A wrong path would find no files and pass the check above.
        try (Stream<Path> files = Files.walk(ENGINE)) {
            assertTrue(files.anyMatch(path -> path.toString().endsWith("CreditAccount.java")),
                    "Expected to find the engine's sources under " + ENGINE.toAbsolutePath());
        }
    }
}
