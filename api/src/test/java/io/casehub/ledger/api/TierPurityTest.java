package io.casehub.ledger.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TierPurityTest {

    @Test
    void apiSourceContainsNoJakartaPersistenceImports() throws IOException {
        Path apiSrc = Path.of("src/main/java");
        if (!Files.exists(apiSrc)) {
            apiSrc = Path.of("api/src/main/java");
        }
        assertThat(apiSrc).as("Cannot locate api source root").exists();

        List<String> violations;
        try (Stream<Path> files = Files.walk(apiSrc)) {
            violations = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> {
                        try {
                            return Files.lines(p)
                                    .filter(line -> line.startsWith("import jakarta.persistence"))
                                    .map(line -> p.getFileName() + ": " + line.trim());
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    .toList();
        }

        assertThat(violations)
                .as("api/ module must contain zero jakarta.persistence imports")
                .isEmpty();
    }
}
