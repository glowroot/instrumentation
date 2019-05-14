/*
 * Copyright 2018-2019 the original author or authors.
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
package org.glowroot.instrumentation.engine.weaving;

import java.io.IOException;
import java.util.Set;

import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.engine.weaving.ClassLoaders.LazyDefinedClass;
import org.glowroot.instrumentation.engine.weaving.InstrumentationDetail.PointcutClass;

class InstrumentationClassRenamer {

    static final String SHIM_SUFFIX = "Shim";
    static final String MIXIN_SUFFIX = "Mixin";

    private static final Logger logger = LoggerFactory.getLogger(InstrumentationClassRenamer.class);

    private final PointcutClass adviceClass;
    private final String rootPackageName;
    private final String bootstrapClassLoaderPackageName;

    private final Set<String> processed = Sets.newHashSet();

    InstrumentationClassRenamer(PointcutClass adviceClass) {
        this.adviceClass = adviceClass;
        String internalName = adviceClass.type().getInternalName();
        int index = internalName.lastIndexOf('/');
        if (index == -1) {
            rootPackageName = "";
        } else {
            rootPackageName = internalName.substring(0, index);
        }
        bootstrapClassLoaderPackageName = rootPackageName + "/boot/";
    }

    @Nullable
    Advice buildNonBootstrapLoaderAdvice(Advice advice) {
        if (rootPackageName.isEmpty()) {
            logger.warn("advice needs to be in a named package in order to collocate the advice in"
                    + " the class loader that it is used from (as opposed to located in the"
                    + " bootstrap class loader)");
            return null;
        } else {
            return ImmutableAdvice.builder()
                    .from(advice)
                    .adviceType(advice.adviceType())
                    .travelerType(advice.travelerType())
                    .isEnabledAdvice(advice.isEnabledAdvice())
                    .onBeforeAdvice(advice.onBeforeAdvice())
                    .onReturnAdvice(advice.onReturnAdvice())
                    .onThrowAdvice(advice.onThrowAdvice())
                    .onAfterAdvice(advice.onAfterAdvice())
                    .isEnabledParameters(advice.isEnabledParameters())
                    .onBeforeParameters(advice.onBeforeParameters())
                    .onReturnParameters(advice.onReturnParameters())
                    .onThrowParameters(advice.onThrowParameters())
                    .onAfterParameters(advice.onAfterParameters())
                    .build();
        }
    }

    @Nullable
    LazyDefinedClass buildNonBootstrapLoaderAdviceClass() throws IOException {
        if (rootPackageName.isEmpty()) {
            logger.warn("advice needs to be in a named package in order to co-locate the advice in"
                    + " the class loader that it is used from (as opposed to located in the"
                    + " bootstrap class loader)");
            return null;
        } else {
            return build(adviceClass.type().getInternalName(), adviceClass.bytes());
        }
    }

    private LazyDefinedClass build(String internalName, byte[] origBytes) throws IOException {
        processed.add(internalName);
        InstrumentationClassRemapper remapper = new InstrumentationClassRemapper();
        ImmutableLazyDefinedClass.Builder builder = ImmutableLazyDefinedClass.builder()
                .type(Type.getObjectType(remapper.mapType(internalName)));
        // TODO don't need a real ClassWriter here, just something that forces visiting everything
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassRemapper(cw, remapper);
        ClassReader cr = new ClassReader(origBytes);
        cr.accept(cv, 0);
        builder.bytes(origBytes);
        for (String unprocessed : remapper.unprocessed) {
            builder.addDependencies(build(unprocessed,
                    InstrumentationDetailBuilder.getBytes(unprocessed, adviceClass.jarFile())));
        }
        return builder.build();
    }

    private class InstrumentationClassRemapper extends Remapper {

        private final Set<String> unprocessed = Sets.newHashSet();

        @Override
        public String map(String internalName) {
            if (collocate(internalName) && !processed.contains(internalName)) {
                unprocessed.add(internalName);
            }
            return internalName;
        }

        private boolean collocate(String internalName) {
            return internalName.startsWith(rootPackageName) && !internalName.endsWith(MIXIN_SUFFIX)
                    && !internalName.endsWith(SHIM_SUFFIX)
                    && !internalName.startsWith(bootstrapClassLoaderPackageName);
        }
    }
}
