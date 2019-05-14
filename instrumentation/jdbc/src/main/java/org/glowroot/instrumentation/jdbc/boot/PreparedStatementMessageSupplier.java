/*
 * Copyright 2015-2019 the original author or authors.
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
package org.glowroot.instrumentation.jdbc.boot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.ConfigListener;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.util.ImmutableList;

public class PreparedStatementMessageSupplier extends QueryMessageSupplier {

    private static final Logger logger = Logger.getLogger(PreparedStatementMessageSupplier.class);

    private static final ConfigService configService = Agent.getConfigService("jdbc");

    private static List<Pattern> captureBindParametersIncludePatterns = Collections.emptyList();
    private static List<Pattern> captureBindParametersExcludePatterns = Collections.emptyList();

    static {
        configService.registerConfigListener(new ConfigListener() {

            @Override
            public void onChange() {
                captureBindParametersIncludePatterns =
                        buildPatternList("captureBindParametersIncludes");
                captureBindParametersExcludePatterns =
                        buildPatternList("captureBindParametersExcludes");
            }

            private List<Pattern> buildPatternList(String propertyName) {
                List<String> values = configService.getListProperty(propertyName).value();
                List<Pattern> patterns = new ArrayList<Pattern>();
                for (String value : values) {
                    try {
                        patterns.add(Pattern.compile(value.trim(), Pattern.DOTALL));
                    } catch (PatternSyntaxException e) {
                        logger.warn("the jdbc instrumentation configuration property {} contains an"
                                + " invalid regular expression: {}\n{}", propertyName, value.trim(),
                                e.getMessage());
                    }
                }
                return ImmutableList.copyOf(patterns);
            }
        });
    }

    // cannot use ImmutableList for parameters since it can contain null elements
    private final @Nullable BindParameterList parameters;
    private final String queryText;

    public PreparedStatementMessageSupplier(@Nullable BindParameterList parameters,
            String queryText) {
        this.parameters = parameters;
        this.queryText = queryText;
    }

    @Override
    public Map<String, List</*@Nullable*/ Object>> get() {
        if (parameters != null && !parameters.isEmpty() && captureBindParameters()) {
            return Collections.singletonMap("parameters", parameters.toDetailList());
        } else {
            return Collections.emptyMap();
        }
    }

    private boolean captureBindParameters() {
        String queryTextTrimmed = queryText.trim();
        boolean include = false;
        for (Pattern includePattern : captureBindParametersIncludePatterns) {
            if (includePattern.matcher(queryTextTrimmed).matches()) {
                include = true;
                break;
            }
        }
        if (!include) {
            return false;
        }
        for (Pattern excludePattern : captureBindParametersExcludePatterns) {
            if (excludePattern.matcher(queryTextTrimmed).matches()) {
                return false;
            }
        }
        return true;
    }
}
