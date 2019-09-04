/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.instrumentation.engine.init;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.Sets;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PreInitializeWeavingClassesTest {

    @Test
    public void shouldCheckHardcodedListAgainstReality() throws IOException {
        GlobalCollector globalCollector = new GlobalCollector();
        // "call" AnalyzedWorld constructor to capture types used by LoadingCache
        // (so these types will be in the list of possible subtypes later on)
        globalCollector.processMethodFailIfNotFound(
                ReferencedMethod.create("org/glowroot/instrumentation/engine/weaving/AnalyzedWorld",
                        "<init>",
                        "(Lcom/google/common/base/Supplier;Ljava/util/List;Ljava/util/List;Z"
                                + "Ljava/lang/instrument/Instrumentation"
                                + ";Lorg/glowroot/instrumentation/engine/weaving"
                                + "/PreloadSomeSuperTypesCache;)V"));
        // "call" WeavingClassFileTransformer constructor
        globalCollector.processMethodFailIfNotFound(
                ReferencedMethod.create(
                        "org/glowroot/instrumentation/engine/weaving/WeavingClassFileTransformer",
                        "<init>", "(Lorg/glowroot/instrumentation/engine/weaving/Weaver;"
                                + "Ljava/lang/instrument/Instrumentation;Ljava/util/List;)V"));
        // "call" WeavingClassFileTransformer.transform()
        globalCollector.processMethodFailIfNotFound(
                ReferencedMethod.create(
                        "org/glowroot/instrumentation/engine/weaving/WeavingClassFileTransformer",
                        "transform", "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/Class;"
                                + "Ljava/security/ProtectionDomain;[B)[B"));
        globalCollector.processOverrides();
        // these assertions just help for debugging, since it can be hard to see the differences in
        // the very large lists below in the "real" assertion
        List<String> globalCollectorUsedTypes = globalCollector.usedInternalNames();
        globalCollectorUsedTypes.removeAll(PreInitializeWeavingClasses.maybeUsedTypes());
        List<String> usedTypes = PreInitializeWeavingClasses.usedTypes();
        if (PreInitializeWeavingClasses.isGuavaPriorTo24()) {
            usedTypes.addAll(PreInitializeWeavingClasses.getGuava20UsedTypes());
        } else {
            usedTypes.addAll(PreInitializeWeavingClasses.getGuava27UsedTypes());
        }
        assertThat(Sets.difference(Sets.newHashSet(globalCollectorUsedTypes),
                Sets.newHashSet(usedTypes))).isEmpty();
        assertThat(Sets.difference(Sets.newHashSet(usedTypes),
                Sets.newHashSet(globalCollectorUsedTypes))).isEmpty();
        assertThat(usedTypes).hasSameSizeAs(globalCollectorUsedTypes);
    }
}
