package com.codebridge.backend.maven;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenDependencyManagementTest {

    private MavenPomParser parser;

    @BeforeEach
    void setUp() {
        parser = new MavenPomParser();
    }

    @Test
    void shouldResolveVersionFromDependencyManagement() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>2.0.17</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<MavenDependency> dependencies =
                parser.parseDependencies(xml);

        assertEquals(1, dependencies.size());
        assertEquals("org.slf4j", dependencies.get(0).groupId());
        assertEquals("slf4j-api", dependencies.get(0).artifactId());
        assertEquals("2.0.17", dependencies.get(0).version());
        assertEquals("compile", dependencies.get(0).scope());
    }

    @Test
    void shouldPreferExplicitDependencyVersion() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>2.0.17</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>1.7.36</version>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<MavenDependency> dependencies =
                parser.parseDependencies(xml);

        assertEquals(1, dependencies.size());
        assertEquals("1.7.36", dependencies.get(0).version());
    }

    @Test
    void shouldResolvePropertyInManagedVersion() {

        String xml = """
                <project>
                    <properties>
                        <slf4j.version>2.0.17</slf4j.version>
                    </properties>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>${slf4j.version}</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<MavenDependency> dependencies =
                parser.parseDependencies(xml);

        assertEquals(1, dependencies.size());
        assertEquals("2.0.17", dependencies.get(0).version());
    }

    @Test
    void shouldNotCountManagedDependenciesAsDirectDependencies() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>2.0.17</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """;

        List<MavenDependency> dependencies =
                parser.parseDependencies(xml);

        assertTrue(dependencies.isEmpty());
    }

    @Test
    void shouldKeepVersionNullWhenNotManaged() {

        String xml = """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<MavenDependency> dependencies =
                parser.parseDependencies(xml);

        assertEquals(1, dependencies.size());
        assertNull(dependencies.get(0).version());
    }

    @Test
    void shouldNotUseImportedBomVersionAsDependencyVersion() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-bom</artifactId>
                                <version>3.0.0</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-bom</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<MavenDependency> dependencies =
                parser.parseDependencies(xml);

        assertEquals(1, dependencies.size());
        assertNull(dependencies.get(0).version());
    }

    @Test
    void shouldNotApplyUnresolvedManagedVersion() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-library</artifactId>
                                <version>${missing.version}</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<MavenDependency> dependencies =
                parser.parseDependencies(xml);

        assertEquals(1, dependencies.size());
        assertNull(dependencies.get(0).version());
    }

    @Test
    void shouldPreserveDeclaredDependencyScope() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.junit.jupiter</groupId>
                                <artifactId>junit-jupiter</artifactId>
                                <version>5.12.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<MavenDependency> dependencies =
                parser.parseDependencies(xml);

        assertEquals(1, dependencies.size());
        assertEquals("5.12.0", dependencies.get(0).version());
        assertEquals("test", dependencies.get(0).scope());
    }
}
