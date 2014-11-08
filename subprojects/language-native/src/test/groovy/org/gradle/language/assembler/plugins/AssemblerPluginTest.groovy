/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.assembler.plugins

import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.assembler.AssemblerSourceSet
import org.gradle.language.assembler.tasks.Assemble
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.nativeplatform.*
import org.gradle.util.GFileUtils
import org.gradle.util.TestUtil
import spock.lang.Specification

class AssemblerPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def "creates asm source set with conventional locations for components"() {
        when:
        dsl {
            apply plugin: AssemblerPlugin
            model {
                components {
                    exe(NativeExecutableSpec)
                }
            }
        }

        then:
        def exe = project.componentSpecs.exe
        exe.sources instanceof FunctionalSourceSet
        exe.sources.asm instanceof AssemblerSourceSet
        exe.sources.asm.source.srcDirs == [project.file("src/exe/asm")] as Set

        and:
        project.sources as Set == exe.sources as Set
    }

    def "can configure source set locations"() {
        given:
        dsl {
            apply plugin: AssemblerPlugin
            model {
                components {
                    exe(NativeExecutableSpec) {
                        sources {
                            asm {
                                source {
                                    srcDirs "d1", "d2"
                                }
                            }
                        }
                    }
                }
            }
        }

        expect:
        project.componentSpecs.exe.sources.asm.source.srcDirs*.name == ["d1", "d2"]
    }

    def "creates assemble tasks for each non-empty executable source set "() {
        when:
        touch("src/test/asm/dummy.s")
        touch("src/test/anotherOne/dummy.s")
        dsl {
            apply plugin: AssemblerPlugin

            model {
                components {
                    test(NativeExecutableSpec) {
                        sources {
                            anotherOne(AssemblerSourceSet) {}
                            emptyOne(AssemblerSourceSet) {}
                        }
                        binaries.all { NativeBinary binary ->
                            binary.assembler.args "ARG1", "ARG2"
                        }
                    }
                }
            }
        }

        then:
        NativeExecutableBinarySpec binary = project.binaries.testExecutable
        binary.tasks.withType(Assemble)*.name == ["assembleTestExecutableTestAnotherOne", "assembleTestExecutableTestAsm"]

        and:
        binary.tasks.withType(Assemble).each { compile ->
            compile instanceof Assemble
            compile.toolChain == binary.toolChain
            compile.assemblerArgs == ["ARG1", "ARG2"]
        }

        and:
        def linkTask = binary.tasks.link
        linkTask TaskDependencyMatchers.dependsOn("assembleTestExecutableTestAnotherOne", "assembleTestExecutableTestAsm")
    }

    def "creates assemble tasks for each library source set"() {
        when:
        touch("src/test/asm/dummy.s")
        touch("src/test/anotherOne/dummy.s")
        dsl {
            apply plugin: AssemblerPlugin
            model {
                components {
                    test(NativeLibrarySpec) {
                        sources {
                            anotherOne(AssemblerSourceSet) {}
                            emptyOne(AssemblerSourceSet) {}
                        }
                        binaries.all {
                            assembler.args "ARG1", "ARG2"
                        }
                        binaries.withType(SharedLibraryBinarySpec) {
                            assembler.args "SHARED1", "SHARED2"
                        }
                        binaries.withType(StaticLibraryBinarySpec) {
                            assembler.args "STATIC1", "STATIC2"
                        }
                    }
                }
            }
        }

        then:
        SharedLibraryBinarySpec sharedLib = project.binaries.testSharedLibrary
        sharedLib.tasks.withType(Assemble)*.name == ["assembleTestSharedLibraryTestAnotherOne", "assembleTestSharedLibraryTestAsm"]
        sharedLib.tasks.withType(Assemble).each { compile ->
            compile.toolChain == sharedLib.toolChain
            compile.assemblerArgs == ["ARG1", "ARG2", "SHARED1", "SHARED2"]
        }
        def sharedLinkTask = sharedLib.tasks.link
        sharedLinkTask TaskDependencyMatchers.dependsOn("assembleTestSharedLibraryTestAnotherOne", "assembleTestSharedLibraryTestAsm")

        and:
        StaticLibraryBinarySpec staticLib = project.binaries.testStaticLibrary
        staticLib.tasks.withType(Assemble)*.name == ["assembleTestStaticLibraryTestAnotherOne", "assembleTestStaticLibraryTestAsm"]
        staticLib.tasks.withType(Assemble).each { compile ->
            compile.toolChain == sharedLib.toolChain
            compile.assemblerArgs == ["ARG1", "ARG2", "STATIC1", "STATIC2"]
        }
        def staticLibTask = staticLib.tasks.createStaticLib
        staticLibTask TaskDependencyMatchers.dependsOn("assembleTestStaticLibraryTestAnotherOne", "assembleTestStaticLibraryTestAsm")
    }

    def touch(String filePath) {
        GFileUtils.touch(project.file(filePath))
    }

    def dsl(Closure closure) {
        closure.delegate = project
        closure()
        project.evaluate()
    }
}
