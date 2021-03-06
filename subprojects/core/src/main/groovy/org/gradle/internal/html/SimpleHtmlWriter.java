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

package org.gradle.internal.html;

import org.gradle.internal.xml.SimpleMarkupWriter;

import java.io.IOException;
import java.io.Writer;

/**
 * <p>A streaming HTML writer.</p>
 */
public class SimpleHtmlWriter extends SimpleMarkupWriter {

    private final Writer output;
    
    public SimpleHtmlWriter(Writer writer) throws IOException {
        this(writer, null);
    }

    public SimpleHtmlWriter(Writer writer, String indent) throws IOException {
        this(writer, indent, true);
    }
    
    private SimpleHtmlWriter(Writer writer, String indent, boolean beginHeader) throws IOException {
        super(writer, indent);
        this.output = writer;
        if (beginHeader) {
            writeHtmlHeader();
        }
    }

    private void writeHtmlHeader() throws IOException {
        writeRaw("<!DOCTYPE html>");
    }
    
    /**
     * Return writer based on same output stream but with custom indentation
     */
    public SimpleHtmlWriter getSubWriter(String indent) throws IOException {
        return new SimpleHtmlWriter(this.output, indent, false);
    }
}
