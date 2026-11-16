
package com.codebridge.backend.maven;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenDependencyIdentityTest {

    private MavenPomParser parser;

    @BeforeEach
    void setUp() {
        parser = new MavenPomParser();
    }

    @Test
    void shouldMatchDefaultJarType() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-library</artifactId>
                                <version>1.2.3</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                            <type>jar</type>
                        </dependency>
                    </dependencies>
                </project>
                """;

        assertEquals("1.2.3", singleDependency(xml).version());
    }

    @Test
    void shouldNotApplyTestJarVersionToNormalJar() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-library</artifactId>
                                <version>2.0.0</version>
                                <type>test-jar</type>
                                <classifier>tests</classifier>
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

        assertNull(singleDependency(xml).version());
    }

    @Test
    void shouldApplyManagedVersionWhenTypeAndClassifierMatch() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-library</artifactId>
                                <version>2.0.0</version>
                                <type>test-jar</type>
                                <classifier>tests</classifier>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                            <type>test-jar</type>
                            <classifier>tests</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """;

        assertEquals("2.0.0", singleDependency(xml).version());
    }

    @Test
    void shouldNotMatchDifferentClassifiers() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-library</artifactId>
                                <version>3.0.0</version>
                                <classifier>tests</classifier>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                            <classifier>sources</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """;

        assertNull(singleDependency(xml).version());
    }

    @Test
    void shouldNotMatchDifferentTypes() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-library</artifactId>
                                <version>3.0.0</version>
                                <type>pom</type>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                            <type>jar</type>
                        </dependency>
                    </dependencies>
                </project>
                """;

        assertNull(singleDependency(xml).version());
    }

    @Test
    void shouldPreferExplicitVersionOverManagedVersion() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-library</artifactId>
                                <version>3.0.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                            <version>4.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;

        assertEquals("4.0.0", singleDependency(xml).version());
    }

    @Test
    void shouldNotMatchUnresolvedClassifierProperty() {

        String xml = """
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-library</artifactId>
                                <version>5.0.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                            <classifier>${missing.classifier}</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """;

        assertNull(singleDependency(xml).version());
    }

    @Test
    void shouldResolveTypeAndClassifierProperties() {

        String xml = """
                <project>
                    <properties>
                        <dependency.type>test-jar</dependency.type>
                        <dependency.classifier>tests</dependency.classifier>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>example-library</artifactId>
                                <version>6.0.0</version>
                                <type>${dependency.type}</type>
                                <classifier>${dependency.classifier}</classifier>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>example-library</artifactId>
                            <type>test-jar</type>
                            <classifier>tests</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """;

        assertEquals("6.0.0", singleDependency(xml).version());
    }

    private MavenDependency singleDependency(String xml) {

        List<MavenDependency> dependencies =
                parser.parseDependencies(xml);

        assertEquals(
                1,
                dependencies.size(),
                "Expected exactly one direct dependency"
        );

        return dependencies.get(0);
    }
}
