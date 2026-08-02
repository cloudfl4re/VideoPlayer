package com.github.squi2rel.vp.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResidenceReflectionTest {
    @TempDir
    Path directory;

    @Test
    void exactLookupIgnoresUnrelatedMethodWithMissingOptionalType() throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources/fixture"));
        Path classes = Files.createDirectories(directory.resolve("classes"));
        Path missingSource = sources.resolve("MissingType.java");
        Path targetSource = sources.resolve("Target.java");
        Files.writeString(missingSource, """
                package fixture;
                public final class MissingType {
                }
                """);
        Files.writeString(targetSource, """
                package fixture;
                public final class Target {
                    public static String availableStatic() {
                        return "static";
                    }
                    public String available() {
                        return "virtual";
                    }
                    public MissingType unavailable() {
                        return null;
                    }
                }
                """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(
                null,
                null,
                null,
                "-d",
                classes.toString(),
                missingSource.toString(),
                targetSource.toString()
        ));
        Files.delete(classes.resolve("fixture/MissingType.class"));

        URL[] urls = {classes.toUri().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Class<?> targetType = Class.forName("fixture.Target", true, loader);
            assertThrows(NoClassDefFoundError.class, () -> targetType.getMethods());

            ResidenceReflection.Handle staticMethod = ResidenceReflection.staticMethod(
                    targetType,
                    "availableStatic",
                    String.class
            );
            assertEquals("static", ResidenceReflection.invoke(staticMethod));

            Object target = targetType.getConstructor().newInstance();
            ResidenceReflection.Handle virtualMethod = ResidenceReflection.virtualMethod(
                    targetType,
                    "available",
                    String.class
            );
            assertEquals("virtual", ResidenceReflection.invoke(virtualMethod, target));
        }
    }
}
