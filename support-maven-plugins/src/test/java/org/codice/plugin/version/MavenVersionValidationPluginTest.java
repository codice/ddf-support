/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General private License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General private License for more details. A copy of the GNU Lesser General private License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 **/

package org.codice.plugin.version;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class MavenVersionValidationPluginTest {

    @Test
    public void testInvalidInputs() {
        List<String> invalidInputs = Arrays.asList("^2.3.1",
                "~2.3.1",
                "<2.3.1",
                "<=2.3.1",
                "2.3.1-5.6",
                ">=2.3.1 || 4.1.0",
                "2.3.1 || >4.1.0",
                "2.3.1 || 4.1.0");

        MavenVersionValidationPlugin mvnPlugin = new MavenVersionValidationPlugin();

        for (String testInputVersion : invalidInputs) {
            assertThat("Assert that each character is properly output as invalid",
                    mvnPlugin.scanTokenForRangeSymbol(testInputVersion),
                    notNullValue());
        }
    }

    @Test
    public void testValidInputs() {
        List<String> validInputs = Arrays.asList(
                "2.3.1",
                "2.3",
                "2.3-beta2",
                "bower",
                "349874875jdakjlfkjadsf#jdfaisdf",
                "3.5.3jdakjlfkjadsf#jdfaisdf");

        MavenVersionValidationPlugin mvnPlugin = new MavenVersionValidationPlugin();

        for (String testInputVersion : validInputs) {
            assertThat("Assert that good version returns null",
                    mvnPlugin.scanTokenForRangeSymbol(testInputVersion),
                    nullValue());
        }
    }
}
