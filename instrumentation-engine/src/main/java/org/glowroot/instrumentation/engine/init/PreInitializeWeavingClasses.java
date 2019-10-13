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

import java.util.LinkedHashMap;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// "There are some things that agents are allowed to do that simply should not be permitted"
//
// -- http://mail.openjdk.java.net/pipermail/hotspot-dev/2012-March/005464.html
//
// in particular (at least prior to parallel class loading in JDK 7) initializing other classes
// inside of a ClassFileTransformer.transform() method occasionally leads to deadlocks
//
// this is still a problem in JDK 7+, since parallel class loading must be opted in to by custom
// class loaders, see ClassLoader.registerAsParallelCapable()
//
// to avoid initializing other classes inside of the transform() method, all classes referenced from
// WeavingClassFileTransformer are pre-initialized (and all classes referenced from those classes,
// etc)
class PreInitializeWeavingClasses {

    private static final Logger logger = LoggerFactory.getLogger(PreInitializeWeavingClasses.class);

    // this is probably not needed, since preInitializeLinkedHashMapKeySetAndKeySetIterator() is
    // only called a single time, but just to be safe ...
    public static volatile @Nullable Object toPreventDeadCodeElimination;

    private PreInitializeWeavingClasses() {}

    static void preInitializeClasses() {
        ClassLoader loader = PreInitializeWeavingClasses.class.getClassLoader();
        for (String type : usedTypes()) {
            initialize(type, loader, true);
        }
        if (isGuavaPriorTo24()) {
            for (String type : getGuava20UsedTypes()) {
                initialize(type, loader, true);
            }
        } else {
            for (String type : getGuava27UsedTypes()) {
                initialize(type, loader, true);
            }
        }
        for (String type : maybeUsedTypes()) {
            initialize(type, loader, false);
        }
        for (String type : javaUsedTypes()) {
            // passing warnOnNotExists=false since ThreadLocalRandom only exists in jdk 1.7+
            initialize(type, loader, false);
        }
        preInitializeLinkedHashMapKeySetAndKeySetIterator();
    }

    private static void initialize(String type, @Nullable ClassLoader loader,
            boolean warnOnNotExists) {
        try {
            Class.forName(type, true, loader);
        } catch (ClassNotFoundException e) {
            if (warnOnNotExists) {
                logger.warn("class not found: {}", type);
            }
            // log exception at trace level
            logger.trace(e.getMessage(), e);
        }
    }

    @VisibleForTesting
    static List<String> usedTypes() {
        List<String> types = Lists.newArrayList();
        types.addAll(getGuavaUsedTypes());
        types.addAll(getEngineUsedTypes());
        types.addAll(getInstrumentationApiUsedTypes());
        types.addAll(getAsmUsedTypes());
        return types;
    }

    private static List<String> getGuavaUsedTypes() {
        List<String> types = Lists.newArrayList();
        types.add("com.google.common.base.Charsets");
        types.add("com.google.common.base.ExtraObjectsMethodsForWeb");
        types.add("com.google.common.base.Function");
        types.add("com.google.common.base.Joiner");
        types.add("com.google.common.base.MoreObjects");
        types.add("com.google.common.base.MoreObjects$1");
        types.add("com.google.common.base.MoreObjects$ToStringHelper");
        types.add("com.google.common.base.MoreObjects$ToStringHelper$ValueHolder");
        types.add("com.google.common.base.Objects");
        types.add("com.google.common.base.PatternCompiler");
        types.add("com.google.common.base.Platform");
        types.add("com.google.common.base.Platform$1");
        types.add("com.google.common.base.Platform$JdkPatternCompiler");
        types.add("com.google.common.base.Preconditions");
        types.add("com.google.common.base.StandardSystemProperty");
        types.add("com.google.common.base.Supplier");
        types.add("com.google.common.base.Throwables");
        types.add("com.google.common.base.Ticker");
        types.add("com.google.common.base.Ticker$1");
        types.add("com.google.common.collect.AbstractIndexedListIterator");
        types.add("com.google.common.collect.ByFunctionOrdering");
        types.add("com.google.common.collect.CollectPreconditions");
        types.add("com.google.common.collect.Collections2");
        types.add("com.google.common.collect.ComparatorOrdering");
        types.add("com.google.common.collect.Hashing");
        types.add("com.google.common.collect.ImmutableCollection");
        types.add("com.google.common.collect.ImmutableCollection$ArrayBasedBuilder");
        types.add("com.google.common.collect.ImmutableCollection$Builder");
        types.add("com.google.common.collect.ImmutableList");
        types.add("com.google.common.collect.ImmutableList$Builder");
        types.add("com.google.common.collect.ImmutableList$ReverseImmutableList");
        types.add("com.google.common.collect.ImmutableList$SubList");
        types.add("com.google.common.collect.ImmutableMap");
        types.add("com.google.common.collect.ImmutableMap$Builder");
        types.add("com.google.common.collect.ImmutableSet");
        types.add("com.google.common.collect.ImmutableSet$Builder");
        types.add("com.google.common.collect.Iterables");
        types.add("com.google.common.collect.Iterators");
        types.add("com.google.common.collect.Lists");
        types.add("com.google.common.collect.Lists$RandomAccessReverseList");
        types.add("com.google.common.collect.Lists$ReverseList");
        types.add("com.google.common.collect.Lists$ReverseList$1");
        types.add("com.google.common.collect.Maps");
        types.add("com.google.common.collect.Maps$1");
        types.add("com.google.common.collect.Maps$EntryFunction");
        types.add("com.google.common.collect.Maps$EntryFunction$1");
        types.add("com.google.common.collect.Maps$EntryFunction$2");
        types.add("com.google.common.collect.ObjectArrays");
        types.add("com.google.common.collect.Ordering");
        types.add("com.google.common.collect.Platform");
        types.add("com.google.common.collect.RegularImmutableList");
        types.add("com.google.common.collect.RegularImmutableMap");
        types.add("com.google.common.collect.RegularImmutableMap$KeySet");
        types.add("com.google.common.collect.RegularImmutableSet");
        types.add("com.google.common.collect.Sets");
        types.add("com.google.common.collect.SingletonImmutableSet");
        types.add("com.google.common.collect.TransformedIterator");
        types.add("com.google.common.collect.TreeTraverser");
        types.add("com.google.common.collect.UnmodifiableIterator");
        types.add("com.google.common.collect.UnmodifiableListIterator");
        types.add("com.google.common.io.ByteSink");
        types.add("com.google.common.io.ByteSource");
        types.add("com.google.common.io.ByteStreams");
        types.add("com.google.common.io.ByteStreams$1");
        types.add("com.google.common.io.Closeables");
        types.add("com.google.common.io.Closer");
        types.add("com.google.common.io.Closer$LoggingSuppressor");
        types.add("com.google.common.io.Closer$SuppressingSuppressor");
        types.add("com.google.common.io.Closer$Suppressor");
        types.add("com.google.common.io.Files");
        types.add("com.google.common.io.Files$1");
        types.add("com.google.common.io.Files$2");
        types.add("com.google.common.io.Files$FileByteSink");
        types.add("com.google.common.io.FileWriteMode");
        types.add("com.google.common.io.LineProcessor");
        types.add("com.google.common.io.Resources");
        types.add("com.google.common.io.Resources$1");
        types.add("com.google.common.io.Resources$UrlByteSource");
        types.add("com.google.common.primitives.Booleans");
        types.add("com.google.common.primitives.Bytes");
        types.add("com.google.common.primitives.Ints");
        return types;
    }

    @VisibleForTesting
    public static List<String> getGuava20UsedTypes() {
        List<String> types = Lists.newArrayList();
        types.add("com.google.common.base.Joiner$1");
        types.add("com.google.common.base.Joiner$MapJoiner");
        types.add("com.google.common.base.Predicate");
        types.add("com.google.common.base.Predicates");
        types.add("com.google.common.base.Predicates$1");
        types.add("com.google.common.base.Predicates$IsEqualToPredicate");
        types.add("com.google.common.base.Predicates$ObjectPredicate");
        types.add("com.google.common.base.Predicates$ObjectPredicate$1");
        types.add("com.google.common.base.Predicates$ObjectPredicate$2");
        types.add("com.google.common.base.Predicates$ObjectPredicate$3");
        types.add("com.google.common.base.Predicates$ObjectPredicate$4");
        types.add("com.google.common.collect.AbstractMapEntry");
        types.add("com.google.common.collect.BiMap");
        types.add("com.google.common.collect.DescendingImmutableSortedSet");
        types.add("com.google.common.collect.ImmutableAsList");
        types.add("com.google.common.collect.ImmutableBiMap");
        types.add("com.google.common.collect.ImmutableEntry");
        types.add("com.google.common.collect.ImmutableEnumMap");
        types.add("com.google.common.collect.ImmutableEnumSet");
        types.add("com.google.common.collect.ImmutableList$1");
        types.add("com.google.common.collect.ImmutableMap$1");
        types.add("com.google.common.collect.ImmutableMap$IteratorBasedImmutableMap");
        types.add("com.google.common.collect.ImmutableMap$IteratorBasedImmutableMap$1EntrySetImpl");
        types.add("com.google.common.collect.ImmutableMapEntry");
        types.add("com.google.common.collect.ImmutableMapEntry$NonTerminalImmutableMapEntry");
        types.add("com.google.common.collect.ImmutableMapEntrySet");
        types.add("com.google.common.collect.ImmutableMapEntrySet$RegularEntrySet");
        types.add("com.google.common.collect.ImmutableMapKeySet");
        types.add("com.google.common.collect.ImmutableMapValues");
        types.add("com.google.common.collect.ImmutableMapValues$1");
        types.add("com.google.common.collect.ImmutableMapValues$2");
        types.add("com.google.common.collect.ImmutableSet$Indexed");
        types.add("com.google.common.collect.ImmutableSet$Indexed$1");
        types.add("com.google.common.collect.ImmutableSortedAsList");
        types.add("com.google.common.collect.ImmutableSortedMap");
        types.add("com.google.common.collect.ImmutableSortedMap$1EntrySet");
        types.add("com.google.common.collect.ImmutableSortedMap$1EntrySet$1");
        types.add("com.google.common.collect.ImmutableSortedMapFauxverideShim");
        types.add("com.google.common.collect.ImmutableSortedSet");
        types.add("com.google.common.collect.ImmutableSortedSetFauxverideShim");
        types.add("com.google.common.collect.Iterators$1");
        types.add("com.google.common.collect.Iterators$10");
        types.add("com.google.common.collect.Iterators$11");
        types.add("com.google.common.collect.Iterators$2");
        types.add("com.google.common.collect.Iterators$3");
        types.add("com.google.common.collect.Iterators$PeekingImpl");
        types.add("com.google.common.collect.Maps$5");
        types.add("com.google.common.collect.Maps$6");
        types.add("com.google.common.collect.Multiset");
        types.add("com.google.common.collect.NaturalOrdering");
        types.add("com.google.common.collect.PeekingIterator");
        types.add("com.google.common.collect.RegularImmutableAsList");
        types.add("com.google.common.collect.RegularImmutableBiMap");
        types.add("com.google.common.collect.RegularImmutableBiMap$1");
        types.add("com.google.common.collect.RegularImmutableBiMap$Inverse");
        types.add("com.google.common.collect.RegularImmutableBiMap$Inverse$InverseEntrySet");
        types.add("com.google.common.collect.RegularImmutableBiMap$Inverse$InverseEntrySet$1");
        types.add("com.google.common.collect.RegularImmutableMap$Values");
        types.add("com.google.common.collect.RegularImmutableSortedSet");
        types.add("com.google.common.collect.ReverseNaturalOrdering");
        types.add("com.google.common.collect.ReverseOrdering");
        types.add("com.google.common.collect.SingletonImmutableBiMap");
        types.add("com.google.common.collect.SingletonImmutableList");
        types.add("com.google.common.collect.SortedIterable");
        types.add("com.google.common.collect.SortedIterables");
        types.add("com.google.common.collect.SortedLists");
        types.add("com.google.common.collect.SortedLists$1");
        types.add("com.google.common.collect.SortedLists$KeyAbsentBehavior");
        types.add("com.google.common.collect.SortedLists$KeyAbsentBehavior$1");
        types.add("com.google.common.collect.SortedLists$KeyAbsentBehavior$2");
        types.add("com.google.common.collect.SortedLists$KeyAbsentBehavior$3");
        types.add("com.google.common.collect.SortedLists$KeyPresentBehavior");
        types.add("com.google.common.collect.SortedLists$KeyPresentBehavior$1");
        types.add("com.google.common.collect.SortedLists$KeyPresentBehavior$2");
        types.add("com.google.common.collect.SortedLists$KeyPresentBehavior$3");
        types.add("com.google.common.collect.SortedLists$KeyPresentBehavior$4");
        types.add("com.google.common.collect.SortedLists$KeyPresentBehavior$5");
        return types;
    }

    @VisibleForTesting
    public static List<String> getGuava27UsedTypes() {
        List<String> types = Lists.newArrayList();
        types.add("com.google.common.base.Absent");
        types.add("com.google.common.base.Optional");
        types.add("com.google.common.base.Strings");
        types.add("com.google.common.collect.ImmutableList$Itr");
        types.add("com.google.common.collect.Iterators$9");
        types.add("com.google.common.collect.RegularImmutableMap$EntrySet");
        types.add("com.google.common.collect.RegularImmutableMap$EntrySet$1");
        types.add("com.google.common.collect.RegularImmutableMap$KeysOrValuesAsList");
        types.add("com.google.common.io.Files$3");
        types.add("com.google.common.graph.SuccessorsFunction");
        types.add("com.google.common.math.IntMath");
        return types;
    }

    private static List<String> getEngineUsedTypes() {
        List<String> types = Lists.newArrayList();
        types.add("org.glowroot.instrumentation.engine.bytecode.api.Bytecode");
        types.add("org.glowroot.instrumentation.engine.bytecode.api.BytecodeService");
        types.add("org.glowroot.instrumentation.engine.bytecode.api.BytecodeServiceHolder");
        types.add("org.glowroot.instrumentation.engine.bytecode.api.ThreadContextPlus");
        types.add(
                "org.glowroot.instrumentation.engine.bytecode.api.ThreadContextThreadLocal$Holder");
        types.add("org.glowroot.instrumentation.engine.bytecode.api.Util");
        types.add("org.glowroot.instrumentation.engine.config.ImmutableAdviceConfig");
        types.add("org.glowroot.instrumentation.engine.config.ImmutableAdviceConfig$Builder");
        types.add("org.glowroot.instrumentation.engine.config.ImmutableAdviceConfig$InitShim");
        types.add("org.glowroot.instrumentation.engine.config.AdviceConfig");
        types.add("org.glowroot.instrumentation.engine.config.AdviceConfig$CaptureKind");
        types.add("org.glowroot.instrumentation.engine.config.AdviceConfig"
                + "$AlreadyInTransactionBehavior");
        types.add("org.glowroot.instrumentation.engine.util.IterableWithSelfRemovableEntries");
        types.add("org.glowroot.instrumentation.engine.util.IterableWithSelfRemovableEntries"
                + "$ElementIterator");
        types.add(
                "org.glowroot.instrumentation.engine.util.IterableWithSelfRemovableEntries$Entry");
        types.add("org.glowroot.instrumentation.engine.util.IterableWithSelfRemovableEntries"
                + "$SelfRemovableEntry");
        types.add("org.glowroot.instrumentation.engine.weaving.Advice");
        types.add("org.glowroot.instrumentation.engine.weaving.AdviceGenerator");
        types.add("org.glowroot.instrumentation.engine.weaving.Advice$AdviceOrdering");
        types.add("org.glowroot.instrumentation.engine.weaving.Advice$AdviceParameter");
        types.add("org.glowroot.instrumentation.engine.weaving.Advice$ParameterKind");
        types.add("org.glowroot.instrumentation.engine.weaving.AdviceAdapter");
        types.add("org.glowroot.instrumentation.engine.weaving.AdviceBuilder");
        types.add("org.glowroot.instrumentation.engine.weaving.AdviceBuilder"
                + "$AdviceConstructionException");
        types.add("org.glowroot.instrumentation.engine.weaving.AdviceMatcher");
        types.add("org.glowroot.instrumentation.engine.weaving.AnalyzedClass");
        types.add("org.glowroot.instrumentation.engine.weaving.AnalyzedMethod");
        types.add("org.glowroot.instrumentation.engine.weaving.AnalyzedWorld");
        types.add(
                "org.glowroot.instrumentation.engine.weaving.AnalyzedWorld$AnalyzedClassAndLoader");
        types.add("org.glowroot.instrumentation.engine.weaving.AnalyzedWorld$ParseContext");
        types.add("org.glowroot.instrumentation.engine.weaving.BootstrapMetaHolders");
        types.add(
                "org.glowroot.instrumentation.engine.weaving.BootstrapMetaHolders$ClassMetaHolder");
        types.add("org.glowroot.instrumentation.engine.weaving.BootstrapMetaHolders"
                + "$MethodMetaHolder");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassAnalyzer");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassAnalyzer$AnalyzedMethodKey");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassAnalyzer"
                + "$BridgeMethodClassVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassAnalyzer"
                + "$BridgeMethodClassVisitor$BridgeMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassAnalyzer$MatchedMixinTypes");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassAnalyzer"
                + "$NonAbstractMethodClassVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassInfoImpl");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassLoaders");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassLoaders$LazyDefinedClass");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassNames");
        types.add("org.glowroot.instrumentation.engine.weaving.FrameDeduppingMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.MethodInfoImpl");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver$ActiveWeaving");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver$ClassLoaderHackClassVisitor");
        types.add(
                "org.glowroot.instrumentation.engine.weaving.Weaver$ClassLoaderHackMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver$JBossUrlHackClassVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver$JBossUrlHackMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver$JBossWeldHackClassVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver$JBossWeldHackMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdvice");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdvice$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdvice$InitShim");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedClassAndLoader");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedClassAndLoader"
                + "$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutablePointcutClass");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutablePointcutClass$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutablePointcutMethod");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutablePointcutMethod$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdviceMatcher");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdviceMatcher$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdviceParameter");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdviceParameter$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedClass");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedClass$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedMethod");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedMethod$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedMethodKey");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedMethodKey$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableBindAnnotation");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableBindAnnotation$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableCatchHandler");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableCatchHandler$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableMatchedMixinTypes");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableMatchedMixinTypes$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableMethodMetaGroup");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableMethodMetaGroup$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableParseContext");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableParseContext$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutablePublicFinalMethod");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutablePublicFinalMethod$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableThinClass");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableThinClass$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableThinMethod");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableThinMethod$Builder");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationSeekerClassVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationSeekerClassVisitor"
                + "$InstrumentationAnnotationMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationSeekerClassVisitor"
                + "$TimerAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationSeekerClassVisitor"
                + "$LocalSpanAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationSeekerClassVisitor"
                + "$TransactionAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.JSRInlinerClassVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.MaybePatterns");
        types.add("org.glowroot.instrumentation.engine.weaving.MixinType");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationClassRenamer");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationClassRenamer"
                + "$InstrumentationClassRemapper");
        types.add(
                "org.glowroot.instrumentation.engine.weaving.InstrumentationDetail$BindAnnotation");
        types.add(
                "org.glowroot.instrumentation.engine.weaving.InstrumentationDetail$PointcutClass");
        types.add(
                "org.glowroot.instrumentation.engine.weaving.InstrumentationDetail$PointcutMethod");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder"
                + "$BindAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder"
                + "$MemberClassVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder"
                + "$MixinMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder"
                + "$PointcutMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder"
                + "$MethodModifierArrayAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder"
                + "$MixinAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder"
                + "$PointcutAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder"
                + "$PointcutAnnotationVisitor$1");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder"
                + "$StringArrayAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.PreloadSomeSuperTypesCache");
        types.add("org.glowroot.instrumentation.engine.weaving.PreloadSomeSuperTypesCache"
                + "$CacheValue");
        types.add("org.glowroot.instrumentation.engine.weaving.PublicFinalMethod");
        types.add("org.glowroot.instrumentation.engine.weaving.ShimType");
        types.add("org.glowroot.instrumentation.engine.weaving.ThinClassVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.ThinClassVisitor"
                + "$AnnotationCaptureMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.ThinClassVisitor"
                + "$RemoteAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.ThinClassVisitor"
                + "$ValueAnnotationVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.ThinClassVisitor$ThinClass");
        types.add("org.glowroot.instrumentation.engine.weaving.ThinClassVisitor$ThinMethod");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver");
        types.add("org.glowroot.instrumentation.engine.weaving.WeavingClassFileTransformer");
        types.add("org.glowroot.instrumentation.engine.weaving.WeavingClassVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.WeavingClassVisitor$InitMixins");
        types.add(
                "org.glowroot.instrumentation.engine.weaving.WeavingClassVisitor$MethodMetaGroup");
        types.add("org.glowroot.instrumentation.engine.weaving.WeavingMethodVisitor");
        types.add("org.glowroot.instrumentation.engine.weaving.WeavingMethodVisitor$CatchHandler");
        return types;
    }

    private static List<String> getInstrumentationApiUsedTypes() {
        List<String> types = Lists.newArrayList();
        types.add("org.glowroot.instrumentation.api.ClassInfo");
        types.add("org.glowroot.instrumentation.api.MethodInfo");
        types.add("org.glowroot.instrumentation.api.OptionalThreadContext");
        types.add("org.glowroot.instrumentation.api.ParameterHolder");
        types.add("org.glowroot.instrumentation.api.ThreadContext");
        types.add("org.glowroot.instrumentation.api.internal.ParameterHolderImpl");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$ClassMeta");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$MethodMeta");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$MethodName");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$OptionalReturn");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$Argument");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$AllArguments");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$This");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$Return");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$Thrown");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$Enter");
        types.add("org.glowroot.instrumentation.api.weaving.Bind$Special");
        types.add("org.glowroot.instrumentation.api.weaving.Advice$IsEnabled");
        types.add("org.glowroot.instrumentation.api.weaving.Advice$MethodModifier");
        types.add("org.glowroot.instrumentation.api.weaving.Advice$OnMethodAfter");
        types.add("org.glowroot.instrumentation.api.weaving.Advice$OnMethodBefore");
        types.add("org.glowroot.instrumentation.api.weaving.Advice$OnMethodReturn");
        types.add("org.glowroot.instrumentation.api.weaving.Advice$OnMethodThrow");
        types.add("org.glowroot.instrumentation.api.weaving.Advice$Pointcut");
        types.add("org.glowroot.instrumentation.api.weaving.Shim");
        return types;
    }

    private static List<String> getAsmUsedTypes() {
        List<String> types = Lists.newArrayList();
        types.add("org.objectweb.asm.AnnotationVisitor");
        types.add("org.objectweb.asm.AnnotationWriter");
        types.add("org.objectweb.asm.Attribute");
        types.add("org.objectweb.asm.Attribute$Set");
        types.add("org.objectweb.asm.ByteVector");
        types.add("org.objectweb.asm.ClassReader");
        types.add("org.objectweb.asm.ClassTooLargeException");
        types.add("org.objectweb.asm.ClassVisitor");
        types.add("org.objectweb.asm.ClassWriter");
        types.add("org.objectweb.asm.ConstantDynamic");
        types.add("org.objectweb.asm.Context");
        types.add("org.objectweb.asm.CurrentFrame");
        types.add("org.objectweb.asm.Edge");
        types.add("org.objectweb.asm.FieldVisitor");
        types.add("org.objectweb.asm.FieldWriter");
        types.add("org.objectweb.asm.Frame");
        types.add("org.objectweb.asm.Handle");
        types.add("org.objectweb.asm.Handler");
        types.add("org.objectweb.asm.Label");
        types.add("org.objectweb.asm.MethodTooLargeException");
        types.add("org.objectweb.asm.MethodVisitor");
        types.add("org.objectweb.asm.MethodWriter");
        types.add("org.objectweb.asm.ModuleVisitor");
        types.add("org.objectweb.asm.ModuleWriter");
        types.add("org.objectweb.asm.Opcodes");
        types.add("org.objectweb.asm.Symbol");
        types.add("org.objectweb.asm.SymbolTable");
        types.add("org.objectweb.asm.SymbolTable$Entry");
        types.add("org.objectweb.asm.Type");
        types.add("org.objectweb.asm.TypePath");
        types.add("org.objectweb.asm.TypeReference");
        types.add("org.objectweb.asm.commons.AdviceAdapter");
        types.add("org.objectweb.asm.commons.AnnotationRemapper");
        types.add("org.objectweb.asm.commons.ClassRemapper");
        types.add("org.objectweb.asm.commons.FieldRemapper");
        types.add("org.objectweb.asm.commons.GeneratorAdapter");
        types.add("org.objectweb.asm.commons.JSRInlinerAdapter");
        types.add("org.objectweb.asm.commons.JSRInlinerAdapter$Instantiation");
        types.add("org.objectweb.asm.commons.LocalVariablesSorter");
        types.add("org.objectweb.asm.commons.Method");
        types.add("org.objectweb.asm.commons.MethodRemapper");
        types.add("org.objectweb.asm.commons.ModuleRemapper");
        types.add("org.objectweb.asm.commons.ModuleHashesAttribute");
        types.add("org.objectweb.asm.commons.Remapper");
        types.add("org.objectweb.asm.commons.SignatureRemapper");
        types.add("org.objectweb.asm.commons.SimpleRemapper");
        types.add("org.objectweb.asm.signature.SignatureReader");
        types.add("org.objectweb.asm.signature.SignatureVisitor");
        types.add("org.objectweb.asm.signature.SignatureWriter");
        types.add("org.objectweb.asm.tree.AbstractInsnNode");
        types.add("org.objectweb.asm.tree.AnnotationNode");
        types.add("org.objectweb.asm.tree.ClassNode");
        types.add("org.objectweb.asm.tree.FieldInsnNode");
        types.add("org.objectweb.asm.tree.FieldNode");
        types.add("org.objectweb.asm.tree.FrameNode");
        types.add("org.objectweb.asm.tree.IincInsnNode");
        types.add("org.objectweb.asm.tree.InnerClassNode");
        types.add("org.objectweb.asm.tree.InsnList");
        types.add("org.objectweb.asm.tree.InsnNode");
        types.add("org.objectweb.asm.tree.IntInsnNode");
        types.add("org.objectweb.asm.tree.InvokeDynamicInsnNode");
        types.add("org.objectweb.asm.tree.JumpInsnNode");
        types.add("org.objectweb.asm.tree.LabelNode");
        types.add("org.objectweb.asm.tree.LdcInsnNode");
        types.add("org.objectweb.asm.tree.LineNumberNode");
        types.add("org.objectweb.asm.tree.LocalVariableAnnotationNode");
        types.add("org.objectweb.asm.tree.LocalVariableNode");
        types.add("org.objectweb.asm.tree.LookupSwitchInsnNode");
        types.add("org.objectweb.asm.tree.MethodInsnNode");
        types.add("org.objectweb.asm.tree.MethodNode");
        types.add("org.objectweb.asm.tree.MethodNode$1");
        types.add("org.objectweb.asm.tree.ModuleExportNode");
        types.add("org.objectweb.asm.tree.ModuleNode");
        types.add("org.objectweb.asm.tree.ModuleOpenNode");
        types.add("org.objectweb.asm.tree.ModuleProvideNode");
        types.add("org.objectweb.asm.tree.ModuleRequireNode");
        types.add("org.objectweb.asm.tree.MultiANewArrayInsnNode");
        types.add("org.objectweb.asm.tree.ParameterNode");
        types.add("org.objectweb.asm.tree.TableSwitchInsnNode");
        types.add("org.objectweb.asm.tree.TryCatchBlockNode");
        types.add("org.objectweb.asm.tree.TypeAnnotationNode");
        types.add("org.objectweb.asm.tree.TypeInsnNode");
        types.add("org.objectweb.asm.tree.Util");
        types.add("org.objectweb.asm.tree.VarInsnNode");
        return types;
    }

    @VisibleForTesting
    public static List<String> maybeUsedTypes() {
        List<String> types = Lists.newArrayList();
        // these are special classes generated by javac (but not by the eclipse compiler) to handle
        // accessing the private constructor in an enclosed type
        // (see http://stackoverflow.com/questions/2883181)
        // and special classes generated by javac (but not by the eclipse compiler) to handle enum
        // switch statements
        // (see http://stackoverflow.com/questions/1834632/java-enum-and-additional-class-files)
        types.add("org.glowroot.instrumentation.engine.config.ImmutableAdviceConfig$1");
        types.add("org.glowroot.instrumentation.engine.util.IterableWithSelfRemovableEntries$1");
        types.add("org.glowroot.instrumentation.engine.weaving.Advice$1");
        types.add("org.glowroot.instrumentation.engine.weaving.AdviceBuilder$1");
        types.add("org.glowroot.instrumentation.engine.weaving.AnalyzedClass$1");
        types.add("org.glowroot.instrumentation.engine.weaving.AnalyzedMethod$1");
        types.add("org.glowroot.instrumentation.engine.weaving.AnalyzedMethodKey$1");
        types.add("org.glowroot.instrumentation.engine.weaving.BootstrapMetaHolders$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassAnalyzer$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ClassAnalyzer"
                + "$BridgeMethodClassVisitor$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutablePointcutClass$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutablePointcutMethod$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedClass$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedClassAndLoader$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedMethod$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAnalyzedMethodKey$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdvice$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdviceMatcher$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableAdviceParameter$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableBindAnnotation$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableCatchHandler$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableLazyDefinedClass$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableMatchedMixinTypes$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableMethodMetaGroup$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableParseContext$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutablePublicFinalMethod$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableThinClass$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ImmutableThinMethod$1");
        types.add(
                "org.glowroot.instrumentation.engine.weaving.InstrumentationSeekerClassVisitor$1");
        types.add("org.glowroot.instrumentation.engine.weaving.MethodMetaGroup$1");
        types.add("org.glowroot.instrumentation.engine.weaving.ThinClassVisitor$1");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationClassRenamer$1");
        types.add("org.glowroot.instrumentation.engine.weaving.InstrumentationDetailBuilder$1");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver$1");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver$2");
        types.add("org.glowroot.instrumentation.engine.weaving.Weaver$FelixOsgiHackClassVisitor$1");
        types.add(
                "org.glowroot.instrumentation.engine.weaving.Weaver$EclipseOsgiHackClassVisitor$1");
        types.add("org.glowroot.instrumentation.engine.weaving.WeavingClassVisitor$1");
        types.add("org.glowroot.instrumentation.engine.weaving.AdviceMatcher$1");
        types.add("org.glowroot.instrumentation.engine.weaving.WeavingMethodVisitor$1");
        types.add("org.glowroot.instrumentation.engine.weaving.PreloadSomeSuperTypesCache$1");
        types.add("org.glowroot.instrumentation.api.config.ConfigListener");
        return types;
    }

    // for the most part, adding used java types is not needed and will just slow down startup
    // exceptions can be added here
    private static List<String> javaUsedTypes() {
        List<String> types = Lists.newArrayList();
        // pre-initialize ThreadLocalRandom to avoid this error that occurred once during
        // integration tests (ClassLoaderLeakTest):
        //
        // java.lang.ClassCircularityError: sun/nio/ch/Interruptible
        //
        // java.lang.Class.getDeclaredFields0(Native Method)[na:1.8.0_20]
        // java.lang.Class.privateGetDeclaredFields(Class.java:2570)[na:1.8.0_20]
        // java.lang.Class.getDeclaredField(Class.java:2055)[na:1.8.0_20]
        // java.util.concurrent.ThreadLocalRandom.<clinit>(ThreadLocalRandom.java:1092)~[na:1.8.0_20]
        // java.util.concurrent.ConcurrentHashMap.fullAddCount(ConcurrentHashMap.java:2526)~[na:1.8.0_20]
        // java.util.concurrent.ConcurrentHashMap.addCount(ConcurrentHashMap.java:2266)~[na:1.8.0_20]
        // java.util.concurrent.ConcurrentHashMap.putVal(ConcurrentHashMap.java:1070)~[na:1.8.0_20]
        // java.util.concurrent.ConcurrentHashMap.put(ConcurrentHashMap.java:1006)~[na:1.8.0_20]
        // org.glowroot.instrumentation.engine.weaving.AnalyzedWorld.add(AnalyzedWorld.java:156)~[na:0.5-SNAPSHOT]
        // org.glowroot.instrumentation.engine.weaving.AnalyzingClassVisitor.visitEndReturningAnalyzedClass(AnalyzingClassVisitor.java:160)~[na:0.5-SNAPSHOT]
        // org.glowroot.instrumentation.engine.weaving.WeavingClassVisitor.visitEnd(WeavingClassVisitor.java:229)~[na:0.5-SNAPSHOT]
        // org.objectweb.asm.ClassVisitor.visitEnd(Unknown Source)~[na:0.5-SNAPSHOT]
        // org.objectweb.asm.ClassReader.accept(Unknown Source)~[na:0.5-SNAPSHOT]
        // org.objectweb.asm.ClassReader.accept(Unknown Source)~[na:0.5-SNAPSHOT]
        // org.glowroot.instrumentation.engine.weaving.Weaver.weaveInternal(Weaver.java:115)[na:0.5-SNAPSHOT]
        // org.glowroot.instrumentation.engine.weaving.Weaver.weave(Weaver.java:78)[na:0.5-SNAPSHOT]
        // org.glowroot.instrumentation.engine.weaving.WeavingClassFileTransformer.transformInternal(WeavingClassFileTransformer.java:113)[na:0.5-SNAPSHOT]
        // org.glowroot.instrumentation.engine.weaving.WeavingClassFileTransformer.transform(WeavingClassFileTransformer.java:76)[na:0.5-SNAPSHOT]
        // sun.instrument.TransformerManager.transform(TransformerManager.java:188)[na:1.8.0_20]
        // sun.instrument.InstrumentationImpl.transform(InstrumentationImpl.java:428)[na:1.8.0_20]
        // java.lang.Class.getDeclaredFields0(Native Method)[na:1.8.0_20]
        // java.lang.Class.privateGetDeclaredFields(Class.java:2570)[na:1.8.0_20]
        // java.lang.Class.getDeclaredField(Class.java:2055)[na:1.8.0_20]
        // java.util.concurrent.locks.LockSupport.<clinit>(LockSupport.java:404)[na:1.8.0_20]
        // java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:2039)[na:1.8.0_20]
        // java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:1088)[na:1.8.0_20]
        // java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:809)[na:1.8.0_20]
        // java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1067)[na:1.8.0_20]
        // java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1127)[na:1.8.0_20]
        // java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)[na:1.8.0_20]
        // java.lang.Thread.run(Thread.java:745)[na:1.8.0_20]
        types.add("java.util.concurrent.ThreadLocalRandom");
        return types;
    }

    private static void preInitializeLinkedHashMapKeySetAndKeySetIterator() {
        // Resources.toByteArray(), which is used during weaving (see AnalyzedWorld), calls
        // java.io.ExpiringCache.get(), which every 300 executions calls
        // java.io.ExpiringCache.cleanup() (see stacktrace below)
        //
        // sometimes this leads to a ClassCircularityError, e.g.
        //
        // java.lang.ClassCircularityError: java/util/LinkedHashMap$LinkedKeyIterator
        // java.util.LinkedHashMap$LinkedKeySet.iterator(LinkedHashMap.java:539)
        // java.io.ExpiringCache.cleanup(ExpiringCache.java:119)
        // java.io.ExpiringCache.get(ExpiringCache.java:76)
        // java.io.UnixFileSystem.canonicalize(UnixFileSystem.java:152)
        // java.io.File.getCanonicalPath(File.java:618)
        // java.io.FilePermission$1.run(FilePermission.java:215)
        // java.io.FilePermission$1.run(FilePermission.java:203)
        // java.security.AccessController.doPrivileged(Native Method)
        // java.io.FilePermission.init(FilePermission.java:203)
        // java.io.FilePermission.<init>(FilePermission.java:277)
        // sun.net.www.protocol.file.FileURLConnection.getPermission(FileURLConnection.java:225)
        // sun.net.www.protocol.jar.JarFileFactory.getPermission(JarFileFactory.java:156)
        // sun.net.www.protocol.jar.JarFileFactory.getCachedJarFile(JarFileFactory.java:126)
        // sun.net.www.protocol.jar.JarFileFactory.get(JarFileFactory.java:81)
        // sun.net.www.protocol.jar.JarURLConnection.connect(JarURLConnection.java:122)
        // sun.net.www.protocol.jar.JarURLConnection.getInputStream(JarURLConnection.java:150)
        // java.net.URL.openStream(URL.java:1038)
        // com.google.common.io.Resources$UrlByteSource.openStream(Resources.java:72)
        // com.google.common.io.ByteSource.read(ByteSource.java:285)
        // com.google.common.io.Resources.toByteArray(Resources.java:98)
        // org.glowroot.instrumentation.engine.weaving.AnalyzedWorld.createAnalyzedClass(AnalyzedWorld.java:320)
        // org.glowroot.instrumentation.engine.weaving.AnalyzedWorld.getOrCreateAnalyzedClass(AnalyzedWorld.java:232)
        // org.glowroot.instrumentation.engine.weaving.AnalyzedWorld.getSuperClasses(AnalyzedWorld.java:189)
        // org.glowroot.instrumentation.engine.weaving.AnalyzedWorld.getAnalyzedHierarchy(AnalyzedWorld.java:139)
        // org.glowroot.instrumentation.engine.weaving.ClassAnalyzer.<init>(ClassAnalyzer.java:108)
        // org.glowroot.instrumentation.engine.weaving.Weaver.weaveUnderTimer(Weaver.java:144)
        // org.glowroot.instrumentation.engine.weaving.Weaver.weave(Weaver.java:95)
        // org.glowroot.instrumentation.engine.weaving.WeavingClassFileTransformer.transformInternal(WeavingClassFileTransformer.java:86)
        // org.glowroot.instrumentation.engine.weaving.WeavingClassFileTransformer.transform(WeavingClassFileTransformer.java:65)
        // sun.instrument.TransformerManager.transform(TransformerManager.java:188)
        // sun.instrument.InstrumentationImpl.transform(InstrumentationImpl.java:428)
        //
        // but different Java versions have different private implementation classes for
        // LinkedHashMap "key set" and "key set iterator", e.g.
        // Java 8 uses java.util.LinkedHashMap$LinkedKeySet and
        // java.util.LinkedHashMap$LinkedKeyIterator
        // while Java 6 and 7 use java.util.HashMap$KeySet and java.util.LinkedHashMap$KeyIterator
        //
        // so using this code to load the "occasional" dependencies of java.io.ExpiringCache
        // instead of loading them by class name
        toPreventDeadCodeElimination = new LinkedHashMap<Object, Object>().keySet().iterator();
    }

    @VisibleForTesting
    static boolean isGuavaPriorTo24() {
        try {
            Class.forName("com.google.common.collect.BinaryTreeTraverser");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
