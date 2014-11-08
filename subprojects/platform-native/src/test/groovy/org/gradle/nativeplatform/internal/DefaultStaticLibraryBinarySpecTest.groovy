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

package org.gradle.nativeplatform.internal
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.nativeplatform.internal.configure.DefaultNativeBinariesFactory.create

class DefaultStaticLibraryBinarySpecTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir
    def instantiator = new DirectInstantiator()
    final library = BaseComponentSpec.create(DefaultNativeLibrarySpec, new DefaultComponentSpecIdentifier("path", "libName"), new DefaultFunctionalSourceSet("name", instantiator, Stub(ProjectSourceSet)), instantiator)
    def namingScheme = new DefaultBinaryNamingScheme("main", "staticLibrary", [])
    def toolChain = Stub(NativeToolChainInternal)
    def platform = Stub(NativePlatform)
    def buildType = Stub(BuildType)
    final resolver = Stub(NativeDependencyResolver)
    final outputFile = Mock(File)

    def "has useful string representation"() {  
        expect:
        staticLibrary.toString() == "static library 'main:staticLibrary'"
    }

    def getStaticLibrary() {
        create(DefaultStaticLibraryBinarySpec, instantiator, library, namingScheme, resolver, toolChain, Stub(PlatformToolProvider), platform, buildType, new DefaultFlavor("flavorOne"))
    }

    def "can set output file"() {
        given:
        final binary = staticLibrary
        def outputFile = Mock(File)

        when:
        binary.staticLibraryFile = outputFile

        then:
        binary.staticLibraryFile == outputFile
    }

    def "can convert binary to a native dependency"() {
        final binary = staticLibrary
        given:
        def lifecycleTask = Stub(Task)
        binary.buildTask = lifecycleTask
        binary.builtBy(Stub(Task))

        and:
        binary.staticLibraryFile = outputFile

        and:
        final headerDir = tmpDir.createDir("headerDir")
        addSources(binary, headerDir)

        expect:
        binary.headerDirs.files == [headerDir] as Set
        binary.staticLibraryFile == outputFile

        and:
        binary.linkFiles.files == [binary.staticLibraryFile] as Set
        binary.linkFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        binary.linkFiles.toString() == "static library 'main:staticLibrary'"

        and:
        binary.runtimeFiles.files.isEmpty()
        binary.runtimeFiles.buildDependencies.getDependencies(Stub(Task)) == [] as Set
    }

    def "includes additional link files in native dependency"() {
        final binary = staticLibrary
        given:
        binary.staticLibraryFile = outputFile
        def linkFile1 = Mock(File)
        def linkFile2 = Mock(File)
        def additionalLinkFiles = Stub(FileCollection) {
            getFiles() >> [linkFile1, linkFile2]
        }
        binary.additionalLinkFiles(additionalLinkFiles)

        and:
        addSources(binary, tmpDir.createDir("headerDir"))

        expect:
        binary.staticLibraryFile == outputFile
        binary.linkFiles.files == [binary.staticLibraryFile, linkFile1, linkFile2] as Set
    }

    private TestFile addSources(DefaultStaticLibraryBinarySpec binary, def headerDir) {
        def headerDirSet = Stub(SourceDirectorySet) {
            getSrcDirs() >> [headerDir]
        }
        def sourceDirSet = Stub(SourceDirectorySet) {
            getFiles() >> [tmpDir.createFile("input.src")]
        }
        def sourceSet = Stub(HeaderExportingSourceSet) {
            getSource() >> sourceDirSet
            getExportedHeaders() >> headerDirSet
        }
        binary.source sourceSet
        headerDir
    }
}
