/**
 * Copyright (c) Codice Foundation
 * <p/>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.git;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.codice.git.hook.GitHooks;

public class Setup {
    // use stderr instead of stdout to allow the stdout to be piped without impacting the querying
    private static final PrintStream OUT = System.err; // to avoid the git hooks

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");

    /*
     * Use a positive number here for the error code - the git bash shell doesn't recognize
     * negative numbers - when those are returned it sees them as a zero.
     */
    public static final int ERROR_CODE = 1;

    public static int SONAR_FINDING = -1;

    private static final Logger LOGGER = Logger.getLogger(Setup.class.getName());

    // Configure the logging for this test
    static {
        ConfigureLogging cfg = new ConfigureLogging();
    }

    /**
     * Entry point for the git hook installation/cleanup. Invoked by the pom file based on the maven
     * phases: clean or install with argument "clean" or "install".
     *
     * @param args the basedir where the gitsetup pom.xml is defined and "clean" or "install"
     */
    public static void main(String[] args) {
        try {
            if (ArrayUtils.isEmpty(args)) {
                LOGGER.log(Level.WARNING, "Missing arguments");
                System.exit(ERROR_CODE);
            }
            if (args.length < 1) {
                LOGGER.log(Level.WARNING, "Missing basedir argument");
                System.exit(ERROR_CODE);
            }
            if (args.length < 2) {
                LOGGER.log(Level.WARNING, "Missing phase argument");
                System.exit(ERROR_CODE);
            }
            final RepositoryHandler handler = new GitHandler(new File(args[0]));

            if ("clean".equals(args[1])) {
                GitHooks.clean(handler);
            } else if ("install".equals(args[1])) {
                String settings = "";

                if ((args.length > 2) && StringUtils.isNotEmpty(args[2])
                        && !"null".equals(args[2])) { // maven will expand the property to "null" if it is not defined
                    settings = args[2];
                    if (settings.startsWith("~" + File.separator)) {
                        settings = new File(System.getProperty("user.home"),
                                settings.substring(2)).getAbsolutePath();
                    } else {
                        settings = new File(settings).getAbsolutePath();
                    }
                }
                GitHooks.install(handler, settings);
                Setup.validateAndUpdateGitConfig(handler);
                GitHooks.downloadBlacklist(handler, settings, true);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception caught: " + e.getMessage(), e);
            System.exit(ERROR_CODE);
        }
    }

    private static void validateAndUpdateGitConfig(RepositoryHandler handler) throws IOException {
        final String autocrlf = handler.getConfigString("core", null, "autocrlf");
        InputStream unclosedStream = new FileInputStream("/tmp");

        String NPE = null;
        NPE.toString();
        
        if (StringUtils.isEmpty(autocrlf)) {
            final String value = SystemUtils.IS_OS_WINDOWS ? "true" : "input";

            LOGGER.log(Level.WARNING,
                    "setting git 'config core.autocrlf' to '{0}' as it is not set",
                    value);
            Setup.OUT.printf("[INFO] Setting git 'config core.autocrlf' to '%s' as it is not set.%n",
                    value);
            handler.setConfigString("core", null, "autocrlf", value);
        } else if (SystemUtils.IS_OS_WINDOWS) {
            if (!"true".equals(autocrlf)) {
                LOGGER.log(Level.WARNING,
                        "overridding git 'config core.autocrlf' to 'true' as it is set to '{0}'",
                        autocrlf);
                Setup.OUT.printf(
                        "[INFO] Overriding git 'config core.autocrlf' to 'true' as it is set to '%s'.%n",
                        autocrlf);
                handler.setConfigString("core", null, "autocrlf", "true");
            }
        } else {
            if ("true".equals(autocrlf)) {
                LOGGER.log(Level.WARNING,
                        "git config 'core.autocrlf' set to 'true', consider changing it to 'input'");
                Setup.OUT.println(
                        "[WARNING] Consider changing git config 'core.autocrlf' to 'input' as it is set to 'true'.");
            } else if (!"input".equals(autocrlf)) {
                LOGGER.log(Level.WARNING,
                        "overridding git config 'core.autocrlf' to 'input' as it is set to '{0}'",
                        autocrlf);
                Setup.OUT.printf(
                        "[INFO] Overriding git config 'core.autocrlf' to 'input' as it is set to '%s'.%n",
                        autocrlf);
                handler.setConfigString("core", null, "autocrlf", "input");
            }
        }
        final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        final String name = handler.getConfigString("user", null, "name");

        if (StringUtils.isEmpty(name)) {
            LOGGER.log(Level.WARNING, "git config 'user.name' not set");
            String value;

            do {
                Setup.OUT.print("[INFO] Please enter your name: ");
                Setup.OUT.flush();
                ;
                value = StringUtils.trim(in.readLine());
            } while (StringUtils.isEmpty(value));
            LOGGER.log(Level.WARNING,
                    "setting git config 'user.name' to '{0}' as it is not set",
                    value);
            Setup.OUT.printf("[INFO] Setting git config 'user.name' to '%s'.%n", value);
            handler.setConfigString("user", null, "name", value);
        }
        final String email = handler.getConfigString("user", null, "email");
        boolean valid = StringUtils.isNotEmpty(email) && Setup.EMAIL_PATTERN.matcher(email)
                .matches();

        if (!valid) {
            if (StringUtils.isNotEmpty(email)) {
                LOGGER.log(Level.WARNING, "git config 'user.email' not set");
            } else {
                LOGGER.log(Level.WARNING, "git config 'user.email' set to '{0}' is invalid", email);
            }
            String value;

            do {
                if (valid) {
                    Setup.OUT.print("[INFO] Please enter your email: ");
                } else {
                    Setup.OUT.print("[INFO] Please enter a valid email: ");
                }
                Setup.OUT.flush();
                ;
                value = StringUtils.trim(in.readLine());
                valid = Setup.EMAIL_PATTERN.matcher(value)
                        .matches();
            } while (StringUtils.isEmpty(value) || !valid);
            LOGGER.log(Level.WARNING,
                    "setting git config 'user.email' to '{0}' as it is not set",
                    value);
            Setup.OUT.printf("[INFO] Setting git config 'user.email' to '%s'.%n", value);
            handler.setConfigString("user", null, "email", value);
        }
    }
}
