/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.base.plugins;

import org.gradle.api.*;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultLanguageRegistry;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.plugins.CreateSourceTransformTask;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.ModelCreators;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.PolymorphicDomainObjectContainerModelProjection;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultComponentSpecContainer;
import org.gradle.platform.base.internal.DefaultPlatformContainer;

import javax.inject.Inject;
import java.util.Collections;

/**
 * Base plugin for language support.
 *
 * Adds a {@link org.gradle.platform.base.ComponentSpecContainer} named {@code componentSpecs} to the project.
 *
 * For each binary instance added to the binaries container, registers a lifecycle task to create that binary.
 */
@Incubating
public class ComponentModelBasePlugin implements Plugin<ProjectInternal> {

    private final Instantiator instantiator;
    private final ModelRegistry modelRegistry;

    @Inject
    public ComponentModelBasePlugin(Instantiator instantiator, ModelRegistry modelRegistry) {
        this.instantiator = instantiator;
        this.modelRegistry = modelRegistry;
    }

    public void apply(final ProjectInternal project) {
        project.apply(Collections.singletonMap("plugin", LanguageBasePlugin.class));

        // TODO:DAZ Remove this extension: will first need to change ComponentTypeRuleDefinitionHandler not to access ComponentSpecContainer via extension
        DefaultComponentSpecContainer components = project.getExtensions().create("componentSpecs", DefaultComponentSpecContainer.class, instantiator);
        modelRegistry.create(
                ModelCreators.of(ModelReference.of("components", DefaultComponentSpecContainer.class), components)
                        .simpleDescriptor("Project.<init>.components()")
                        .withProjection(new PolymorphicDomainObjectContainerModelProjection<DefaultComponentSpecContainer, ComponentSpec>(components, ComponentSpec.class))
                        .build()
        );
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {
        @Model
        LanguageRegistry languages(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class).newInstance(DefaultLanguageRegistry.class);
        }

        @Mutate
        void initializeSourceSetsForComponents(final ComponentSpecContainer components, final LanguageRegistry languageRegistry, ServiceRegistry serviceRegistry) {
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);

            // TODO:DAZ Using live collections here in order to add 'default' construction for components, which should be executed before any user component configuration is applied.
            languageRegistry.all(new Action<LanguageRegistration<?>>() {
                public void execute(final LanguageRegistration<?> languageRegistration) {
                    final ComponentSourcesRegistrationAction<?> action = ComponentSourcesRegistrationAction.create(languageRegistration, fileResolver, instantiator);
                    components.withType(ComponentSpecInternal.class).all(new Action<ComponentSpecInternal>() {
                        public void execute(ComponentSpecInternal component) {
                            action.execute(component);
                        }
                    });
                }
            });
        }

        // Required because creation of Binaries from Components is not yet wired into the infrastructure
        @Mutate
        void closeComponentsForBinaries(CollectionBuilder<Task> tasks, ComponentSpecContainer components) {
        }

        // Finalizing here, as we need this to run after any 'assembling' task (jar, link, etc) is created.
        @Finalize
        void createSourceTransformTasks(final TaskContainer tasks, final BinaryContainer binaries, LanguageRegistry languageRegistry) {
            for (LanguageRegistration<?> language : languageRegistry) {
                for (BinarySpecInternal binary : binaries.withType(BinarySpecInternal.class)) {
                    final CreateSourceTransformTask createRule = new CreateSourceTransformTask(language);
                    createRule.createCompileTasksForBinary(tasks, binary);
                }
            }
        }

        @Finalize // This is setting defaults for each component in the container. Should not be finalizing the container.
        void applyDefaultSourceConventions(ComponentSpecContainer componentSpecs) {
            for (ComponentSpec componentSpec : componentSpecs) {
                for (LanguageSourceSet languageSourceSet : componentSpec.getSource()) {
                    // Only apply default locations when none explicitly configured
                    if (languageSourceSet.getSource().getSrcDirs().isEmpty()) {
                        languageSourceSet.getSource().srcDir(String.format("src/%s/%s", componentSpec.getName(), languageSourceSet.getName()));
                    }
                }
            }
        }

        // TODO:DAZ Work out why this is required
        @Mutate
        void closeSourcesForBinaries(BinaryContainer binaries, ProjectSourceSet sources) {
            // Only required because sources aren't fully integrated into model
        }

        @Model
        PlatformContainer platforms(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultPlatformContainer.class, instantiator);
        }

        @Mutate
        void registerPlatformExtension(ExtensionContainer extensions, PlatformContainer platforms) {
            extensions.add("platforms", platforms);
        }

    }

    // TODO:DAZ Needs to be a separate action since can't have parameterized utility methods in a RuleSource
    private static class ComponentSourcesRegistrationAction<U extends LanguageSourceSet> implements Action<ComponentSpecInternal> {
        private final LanguageRegistration<U> languageRegistration;
        private final FileResolver fileResolver;
        private final Instantiator instantiator;

        private ComponentSourcesRegistrationAction(LanguageRegistration<U> registration, FileResolver fileResolver, Instantiator instantiator) {
            this.languageRegistration = registration;
            this.fileResolver = fileResolver;
            this.instantiator = instantiator;
        }
        
        public static <U extends LanguageSourceSet> ComponentSourcesRegistrationAction<U> create(LanguageRegistration<U> registration, FileResolver fileResolver, Instantiator instantiator) {
            return new ComponentSourcesRegistrationAction<U>(registration, fileResolver, instantiator);
        }

        public void execute(ComponentSpecInternal componentSpecInternal) {
            registerLanguageSourceSetFactory(componentSpecInternal, fileResolver, instantiator);
            createDefaultSourceSetForComponents(componentSpecInternal);
        }

        void registerLanguageSourceSetFactory(final ComponentSpecInternal component,
                                              final FileResolver fileResolver, final Instantiator instantiator) {
            final FunctionalSourceSet functionalSourceSet = component.getSources();
            NamedDomainObjectFactory<U> namedDomainObjectFactory = new NamedDomainObjectFactory<U>() {
                public U create(String name) {
                    Class<? extends U> sourceSetImplementation = languageRegistration.getSourceSetImplementation();
                    return instantiator.newInstance(sourceSetImplementation, name, functionalSourceSet.getName(), fileResolver);
                }
            };
            functionalSourceSet.registerFactory(languageRegistration.getSourceSetType(), namedDomainObjectFactory);
        }

        void createDefaultSourceSetForComponents(final ComponentSpecInternal component) {
            final FunctionalSourceSet functionalSourceSet = component.getSources();
            if (component.getInputTypes().contains(languageRegistration.getOutputType())) {
                functionalSourceSet.maybeCreate(languageRegistration.getName(), languageRegistration.getSourceSetType());
            }
        }
    }
}
