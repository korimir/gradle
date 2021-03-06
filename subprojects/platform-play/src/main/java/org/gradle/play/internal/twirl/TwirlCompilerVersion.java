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

package org.gradle.play.internal.twirl;

enum TwirlCompilerVersion {

    V_22X("play.templates.ScalaTemplateCompiler", "play.api.templates.HtmlFormat", DEFAULTS.V_22X_DEFAULT_IMPORTS),
    V_102("play.twirl.compiler.TwirlCompiler", "play.twirl.api.HtmlFormat", DEFAULTS.V_102_DEFAULT_IMPORTS);

    private final String compilerClassName;
    private String defaultFormatterType;
    private String defaultAdditionalImports;

    TwirlCompilerVersion(String compilerClassName, String defaultFormatterType, String defaultAdditionalImports) {
        this.compilerClassName = compilerClassName;
        this.defaultFormatterType = defaultFormatterType;
        this.defaultAdditionalImports = defaultAdditionalImports;
    }

    String getCompilerClassname(){
        return compilerClassName;
    }

    static TwirlCompilerVersion parse(String version){
        if(version.startsWith("2.2.")){
            return V_22X;
        }else if(version.equals("1.0.2")){
            return V_102;
        }
        return V_102; // DEFAULT fallback
    }

    public String getDefaultFormatterType() {
        return defaultFormatterType;
    }

    public String getDefaultAdditionalImports() {
        return defaultAdditionalImports;
    }

    static class DEFAULTS {
        static final String V_22X_DEFAULT_IMPORTS = "import play.api.templates._\n"
                + "import play.api.templates.PlayMagic._\n"
                + "import models._\n"
                + "import controllers._\n"
                + "import play.api.i18n._\n"
                + "import play.api.mvc._\n"
                + "import play.api.data._\n"
                + "import views.html._";

        static final String V_102_DEFAULT_IMPORTS = "import controllers._";
    }
}