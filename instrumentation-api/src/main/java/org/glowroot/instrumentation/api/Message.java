/*
 * Copyright 2011-2019 the original author or authors.
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
package org.glowroot.instrumentation.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.checker.PolyNull;
import org.glowroot.instrumentation.api.internal.ReadableMessage;

/**
 * The detail map can contain only {@link String}, {@link Number}, {@link Boolean} and null values.
 * It can also contain nested lists and maps whose values are one of the above types (including
 * other lists and maps). Nested maps cannot have null keys.
 * 
 * The detail map does not need to be thread safe as long as it is only instantiated in response to
 * either MessageSupplier.get() or Message.getDetail() which are called by the thread that needs the
 * map.
 */
public abstract class Message {

    private static final int MESSAGE_CHAR_LIMIT = 100000;

    private static final int MESSAGE_DETAIL_CHAR_LIMIT = 10000;

    private static final String[] EMPTY_ARGS = new String[0];
    private static final Map<String, Object> EMPTY_DETAIL = Collections.emptyMap();

    // accepts null message so callers don't have to check if passing it in from elsewhere
    public static Message create(@Nullable String message) {
        return new MessageImpl(message, EMPTY_ARGS, EMPTY_DETAIL);
    }

    // does not copy args
    public static Message create(String template, @Nullable String... args) {
        return new MessageImpl(template, args, EMPTY_DETAIL);
    }

    // accepts null message so callers don't have to check if passing it in from elsewhere
    public static Message create(@Nullable String message, Map<String, ?> detail) {
        return new MessageImpl(message, EMPTY_ARGS, detail);
    }

    Message() {}

    // implementing ReadableMessage is just a way to access this class by agent implementors without
    // making it (obviously) accessible to instrumentation implementations
    private static class MessageImpl extends Message implements ReadableMessage {

        private static final Logger logger = Logger.getLogger(MessageImpl.class);

        private final @Nullable String template;
        private final @Nullable String[] args;
        private final Map<String, ?> detail;

        private MessageImpl(@Nullable String template, @Nullable String[] args,
                Map<String, ?> detail) {
            this.template = truncateMessageIfNeeded(template);
            for (int i = 0; i < args.length; i++) {
                args[i] = truncateMessageIfNeeded(args[i]);
            }
            this.args = args;
            if (needsTruncateDetail(detail)) {
                this.detail = truncateDetail(detail);
            } else {
                this.detail = detail;
            }
        }

        @Override
        public String getText() {
            if (template == null) {
                return "";
            }
            if (args.length == 0) {
                return template;
            }
            // Matcher.appendReplacement() can't be used here since appendReplacement() applies
            // special meaning to slashes '\' and dollar signs '$' in the replacement text.
            // These special characters can be escaped in the replacement text via
            // Matcher.quoteReplacemenet(), but the implementation below feels slightly more
            // performant and not much more complex
            StringBuilder text = new StringBuilder();
            int curr = 0;
            int next;
            int argIndex = 0;
            while ((next = template.indexOf("{}", curr)) != -1) {
                text.append(template.substring(curr, next));
                if (argIndex < args.length) {
                    // arg may be null but that is ok, StringBuilder will append "null"
                    text.append(args[argIndex++]);
                    curr = next + 2; // +2 to skip over "{}"
                } else {
                    text.append("<not enough args provided for template>");
                    curr = next + 2; // +2 to skip over "{}"
                    logger.warn("not enough args provided for template: {}", template);
                }
            }
            text.append(template.substring(curr));
            return truncateMessageIfNeeded(text.toString());
        }

        @Override
        public Map<String, ?> getDetail() {
            return detail;
        }

        private static @PolyNull String truncateMessageIfNeeded(@PolyNull String s) {
            if (s == null || s.length() <= MESSAGE_CHAR_LIMIT) {
                return s;
            } else {
                return s.substring(0, MESSAGE_CHAR_LIMIT) + " [truncated to " + MESSAGE_CHAR_LIMIT
                        + " characters]";
            }
        }

        private static boolean needsTruncateDetail(Map<?, ?> detail) {
            for (Map.Entry<?, ?> entry : detail.entrySet()) {
                Object key = entry.getKey();
                if (key instanceof String && needsTruncateDetail((String) key)) {
                    return true;
                }
                if (needsTruncateDetail(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean needsTruncateDetail(@Nullable Object value) {
            if (value instanceof Map) {
                return needsTruncateDetail((Map<?, ?>) value);
            } else if (value instanceof List) {
                return needsTruncateDetail((List<?>) value);
            } else if (value instanceof String) {
                return needsTruncateDetail((String) value);
            } else {
                return false;
            }
        }

        private static boolean needsTruncateDetail(List<?> detail) {
            for (Object value : detail) {
                if (needsTruncateDetail(value)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean needsTruncateDetail(String value) {
            return value.length() > MESSAGE_DETAIL_CHAR_LIMIT;
        }

        private static Map<String, ?> truncateDetail(Map<String, ?> detail) {
            // cannot use immutable map since detail map may contain null values
            Map<String, /*@Nullable*/ Object> truncatedDetail =
                    new HashMap<String, /*@Nullable*/ Object>();
            for (Map.Entry<String, ?> entry : detail.entrySet()) {
                truncatedDetail.put(truncateDetailIfNeeded(entry.getKey()),
                        truncate(entry.getValue()));
            }
            return truncatedDetail;
        }

        private static @Nullable Object truncate(@Nullable Object value) {
            if (value instanceof Map) {
                return truncateDetailNested((Map<?, ?>) value);
            } else if (value instanceof List) {
                return truncateDetail((List<?>) value);
            } else if (value instanceof String) {
                return truncateDetailIfNeeded((String) value);
            } else {
                return value;
            }
        }

        private static Map<?, ?> truncateDetailNested(Map<?, ?> detail) {
            // cannot use immutable map since detail map may contain null values
            Map</*@Nullable*/ Object, /*@Nullable*/ Object> truncatedDetail =
                    new HashMap</*@Nullable*/ Object, /*@Nullable*/ Object>();
            for (Map.Entry<?, ?> entry : detail.entrySet()) {
                Object key = entry.getKey();
                if (key instanceof String) {
                    key = truncateDetailIfNeeded((String) key);
                }
                truncatedDetail.put(key, truncate(entry.getValue()));
            }
            return truncatedDetail;
        }

        private static List</*@Nullable*/ Object> truncateDetail(List<?> detail) {
            // cannot use immutable list since detail list may contain null values
            List</*@Nullable*/ Object> truncatedDetail = new ArrayList</*@Nullable*/ Object>();
            for (Object value : detail) {
                truncatedDetail.add(truncate(value));
            }
            return truncatedDetail;
        }

        private static String truncateDetailIfNeeded(String s) {
            if (s.length() <= MESSAGE_DETAIL_CHAR_LIMIT) {
                return s;
            } else {
                return s.substring(0, MESSAGE_DETAIL_CHAR_LIMIT) + " [truncated to "
                        + MESSAGE_DETAIL_CHAR_LIMIT + " characters]";
            }
        }
    }
}
