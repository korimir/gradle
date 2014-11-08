/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit.report;

import java.io.IOException;

import org.gradle.api.internal.tasks.testing.junit.result.TestFailure;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestOutputEvent.Destination;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.reporting.CodePanelRenderer;
import org.gradle.util.GUtil;

class ClassPageRenderer extends PageRenderer<ClassTestResults> {
    private final CodePanelRenderer codePanelRenderer = new CodePanelRenderer();
    private final TestResultsProvider resultsProvider;

    public ClassPageRenderer(TestResultsProvider provider) {
        this.resultsProvider = provider;
    }

    @Override
    protected void renderBreadcrumbs(SimpleHtmlWriter htmlWriter) throws IOException {
        htmlWriter.startElement("div").attribute("class", "breadcrumbs")
            .startElement("a").attribute("href", getResults().getUrlTo(getResults().getParent().getParent())).characters("all").endElement()
            .characters(" > ")
            .startElement("a").attribute("href", getResults().getUrlTo(getResults().getPackageResults())).characters(getResults().getPackageResults().getName()).endElement()
            .characters(String.format(" > %s", getResults().getSimpleName()))
        .endElement();
    }

    private void renderTests(long classId, SimpleHtmlWriter htmlWriter) throws IOException {
        // Write preClass logs
        htmlWriter.startElement("span").attribute("class", "collapsibleOutput").attribute("id", "outputPre" + classId).characters("Class Output").endElement()
        .startElement("span").attribute("class", "code").attribute("id", "coutputPre" + classId).attribute("style", "display:none").startElement("pre").characters("");
            resultsProvider.writeAllOutput(classId, new ClassOutputFilter(htmlWriter, false), htmlWriter);
        htmlWriter.endElement().endElement();
        
        htmlWriter.startElement("br").endElement();
        
        htmlWriter.startElement("table")
            .startElement("thead")
                .startElement("tr")
                    .startElement("th").characters("Test").endElement()
                    .startElement("th").characters("Duration").endElement()
                    .startElement("th").characters("Result").endElement()
                    .startElement("th").characters("Output").endElement()
                .endElement()
        .endElement();

        final StdErrOutputEnricher enricher = new StdErrOutputEnricher(htmlWriter.getSubWriter(null));
        for (TestResult test : getResults().getTestResults()) {
            htmlWriter.startElement("tr")
                .startElement("td").attribute("class", test.getStatusClass()).attribute("id", "r" + test.getId()).characters(test.getName()).endElement()
                .startElement("td").characters(test.getFormattedDuration()).endElement()
                .startElement("td").attribute("class", test.getStatusClass()).characters(test.getFormattedResultType()).endElement()
                .startElement("td").startElement("span").attribute("class", "collapsibleOutput").
                    attribute("id", "output" + classId + "_" + test.getId()).characters("Output").endElement()
                .endElement()
            .endElement();
                    
            htmlWriter.startElement("tr")
                .startElement("td").attribute("colspan", "4")
                    .startElement("span").attribute("class", "code").attribute("id", "coutput" + classId + "_" + test.getId())
                        .attribute("style", "display:none").startElement("pre").characters("");
                    resultsProvider.writeTestOutput(classId, test.getId(), enricher, htmlWriter);
                    htmlWriter.endElement().endElement()
                .endElement()
            .endElement();
        }
        htmlWriter.endElement();
        
        htmlWriter.startElement("br").endElement();
        
        // Write postClass logs
        htmlWriter.startElement("span").attribute("class", "collapsibleOutput").attribute("id", "outputPost" + classId).characters("Class Output").endElement()
        .startElement("span").attribute("class", "code").attribute("id", "coutputPost" + classId).attribute("style", "display:none").startElement("pre").characters("");
            resultsProvider.writeAllOutput(classId, new ClassOutputFilter(htmlWriter, true), htmlWriter);
        htmlWriter.endElement().endElement();
    }

    @Override
    protected void renderFailures(SimpleHtmlWriter htmlWriter) throws IOException {
        for (TestResult test : getResults().getFailures()) {
            htmlWriter.startElement("div").attribute("class", "test")
                .startElement("a").attribute("name", test.getName()).characters("").endElement() //browsers dont understand <a name="..."/>
                .startElement("h3").attribute("class", test.getStatusClass()).characters(test.getName()).endElement();
            for (TestFailure failure : test.getFailures()) {
                String message;
                if (GUtil.isTrue(failure.getMessage()) && !failure.getStackTrace().contains(failure.getMessage())) {
                    message = failure.getMessage() + SystemProperties.getLineSeparator() + SystemProperties.getLineSeparator() + failure.getStackTrace();
                } else {
                    message = failure.getStackTrace();
                }
                codePanelRenderer.render(message, htmlWriter);
            }
            htmlWriter.endElement();
        }
    }

    @Override
    protected void registerTabs() {
        addFailuresTab();
        final long classId = getModel().getId();
        addTab("Tests", new ErroringAction<SimpleHtmlWriter>() {
            public void doExecute(SimpleHtmlWriter writer) throws IOException {
                renderTests(classId, writer);
            }
        });
    }
    
    
    private static class StdErrOutputEnricher implements TestResultsProvider.WriterOutputEnricher {
        private final SimpleHtmlWriter writer;
        
        private TestOutputEvent.Destination prevDestination;
        public StdErrOutputEnricher(SimpleHtmlWriter writer) {
            this.writer = writer;
        }

        public boolean enrichPre(long testId, TestOutputEvent.Destination destination) throws IOException {
            if (prevDestination != destination) {
                if (destination == Destination.StdErr) {
                    writer.startElement("span").attribute("class", "stderr").characters("");
                } else if (prevDestination != null) {
                    writer.endElement();
                }
            }
            prevDestination = destination;
            return true;
        }
        
        public void complete() throws IOException {
            if (prevDestination == Destination.StdErr) {
                writer.endElement();
            }
            prevDestination = null;
        }
    }
    
    private static class ClassOutputFilter extends StdErrOutputEnricher {

        private final boolean acceptOnlyAfter;
        private boolean hasTestClasses;
        
        public ClassOutputFilter(SimpleHtmlWriter writer, boolean acceptOnlyAfter) {
            super(writer);
            this.acceptOnlyAfter = acceptOnlyAfter;
        }
        
        @Override
        public boolean enrichPre(long testId, Destination destination)
                throws IOException {
            if (testId == 0) {
                if (hasTestClasses == acceptOnlyAfter) {
                    return super.enrichPre(testId, destination);
                }
            } else {
                hasTestClasses = true;
            }
            return false;
        }
        
        @Override
        public void complete() throws IOException {
            hasTestClasses = false;
            super.complete();
        }
        
    }
}

