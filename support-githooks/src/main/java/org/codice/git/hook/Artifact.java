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
package org.codice.git.hook;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.codice.git.RepositoryHandler;

import com.google.common.base.Charsets;

/**
 * The artifact class is used to keep track of a particular artifact that can be downloaded via maven.
 */
class Artifact {
    private static final long MILLIS_PER_DAY = 1000L * 60L * 60L * 24L;

    private static final Logger LOGGER = Logger.getLogger(Artifact.class.getName());

    private final RepositoryHandler handler;

    private final String name;

    private final File file;

    private final File infofile;

    private final boolean install;

    private final String iprefix;

    private final String eprefix;

    protected String mvnInfo;

    protected String mvnName;

    /**
     * Instantiates a new artifact.
     *
     * @param handler  the repository handler
     * @param name     the name of the file (used for identification)
     * @param file     the location of the artifact on disk
     * @param infoFile the location where to cache the info for the artifact on disk
     * @param install  <code>true</code> if we are at install time; <code>false</code> otherwise
     */
    Artifact(RepositoryHandler handler, String name, File file, File infoFile, boolean install) {
        this.handler = handler;
        this.name = name;
        this.file = file;
        this.infofile = infoFile;
        this.install = install;
        this.iprefix = (install ? "[INFO] " : "");
        this.eprefix = (install ? "[ERROR] " : "");
    }

    /**
     * Downloads the artifact using maven.
     *
     * @param settings the maven settings file or "" if using the default one
     * @param out      the output stream where to print messages to the user
     * @throws IOException if an error occurs
     */
    protected void downloadUsingMaven(String settings, PrintStream out) throws IOException {
        final CommandLine cmd = new CommandLine(SystemUtils.IS_OS_WINDOWS ? "mvn.cmd" : "mvn");

        cmd.addArgument("-f")
                .addArgument(new File(handler.getBasedir(), "pom.xml").getAbsolutePath());
        if (StringUtils.isNotEmpty(settings)) {
            cmd.addArgument("-s")
                    .addArgument(settings);
        }
        cmd.addArgument("org.apache.maven.plugins:maven-dependency-plugin:3.0.0:copy")
                .addArgument("-Dartifact=" + mvnInfo)
                .addArgument("-DoutputDirectory=" + handler.getBasedir()
                        .getAbsolutePath())
                .addArgument("-Dmdep.stripClassifier=true")
                .addArgument("-Dmdep.stripVersion=true");
        if (!install) {
            cmd.addArgument("-quiet");
        }
        final DefaultExecutor exec = new DefaultExecutor();

        exec.setExitValue(0);
        try {
            exec.execute(cmd);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "failed to download blacklist words artifact", e);
            if (file.exists()) { // ignore the error and continue with the one that is there
                return;
            }
            out.printf("%sFailed to download artifact '%s'; %s.%n",
                    eprefix,
                    mvnInfo,
                    e.getMessage());
            throw new IOException("failed to download blacklist words artifact", e);
        }
        if (!mvnName.equals(file.getName())) {
            final File f = new File(handler.getBasedir(), mvnName);

            out.printf("%sMoving %s to %s.%n", iprefix, mvnName, file);
            if (!f.renameTo(file)) {
                LOGGER.log(Level.WARNING, "failed to copy {0} file", file.getName());
                if (file.exists()) { // ignore the error and continue with the one that is there
                    return;
                }
                out.printf("%sFailed to move %s to %s.%n", eprefix, mvnName, file);
                throw new IOException("failed to copy " + file.getName() + " file");
            }
        }
    }

    /**
     * Gets the maven information for the artifact.
     *
     * @return the maven info for the artifact or <code>null</code> if none defined yet
     */
    public String getMavenInfo() {
        return mvnInfo;
    }

    /**
     * Gets the maven name for the artifact.
     *
     * @return the maven name for the artifact or <code>null</code> if none defined yet
     */
    public String getMavenName() {
        return mvnName;
    }

    /**
     * Reads the cached artifact info.
     * </p>
     * <i>Note:</i> All exceptions are swallowed up and the info is cleared if an error occurs.
     */
    public void readInfo() {
        this.mvnInfo = null;
        this.mvnName = null;
        if (!infofile.exists()) {
            return;
        }
        try {
            final String content = FileUtils.readFileToString(infofile, Charsets.UTF_8);
            final int i = content.indexOf(',');

            LOGGER.log(Level.FINE,
                    "The {0} artifact info cached is: {1}",
                    new Object[] {name, content});
            if (i != -1) {
                this.mvnInfo = content.substring(0, i);
                this.mvnName = content.substring(i + 1);
            } else { // content invalid so continue without reloading
                LOGGER.log(Level.WARNING, "The {0} artifact info is invalid", name);
            }
        } catch (IOException e) { // ignore and continue without reloading
            LOGGER.log(Level.WARNING, "The " + name + " artifact info failed to be loaded", e);
        }
    }

    /**
     * Writes the artifact info to the cache file if it doesn't exist yet.
     * <\p>
     * <i>Note:</i> All exceptions are swallowed up if an error occurs.
     */
    public void writeInfo() {
        if ((mvnInfo != null) && !infofile.exists()) {
            try {
                LOGGER.log(Level.FINE, "caching {0} artifact info", name);
                FileUtils.writeStringToFile(infofile, mvnInfo + ',' + mvnName, Charsets.UTF_8);
            } catch (IOException e) { // ignore and continue
                LOGGER.log(Level.WARNING, "failed to cache " + name + " artifact info", e);
            }
        }
    }

    /**
     * Prompts for the blacklist artifact info.
     *
     * @param in  the reader to read user input from
     * @param out the output stream where to print messages to the user
     * @throws IOException if an error occurs
     */
    public void promptInfo(BufferedReader in, PrintStream out) throws IOException {
        this.mvnInfo = null;
        this.mvnName = null;
        LOGGER.log(Level.WARNING, "The {0} file was not found.", name);
        out.printf("%sThe %s file was not found%n", iprefix, name);
        out.printf("%sDo you wish to download it automatically (y/n) [Y]? ", iprefix);
        out.flush();
        final String yn = StringUtils.lowerCase(StringUtils.trim(in.readLine()));

        if (!yn.isEmpty() && (yn.charAt(0) == 'n')) {
            out.printf("%s You can always manually copy the file to: %n", iprefix);
            out.printf("%s    %n", iprefix, file);
            if (!infofile.exists()) { // make sure we no longer cache the info
                infofile.delete();
            }
            return;
        }
        out.println(iprefix);
        out.printf("%sPlease provide the maven artifact's coordinates for the %s file:%n",
                iprefix,
                name);
        final String email = handler.getConfigString("user", null, "email");
        String dgroup = null;

        if (StringUtils.isNotEmpty(email)) {
            final int i = email.indexOf('@');

            if (i != -1) {
                final String domain = email.substring(i + 1);
                final int j = domain.indexOf('.');

                if (j != -1) {
                    dgroup = domain.substring(j + 1) + '.' + domain.substring(0, j);
                }
            }
        }
        String group;
        final String id;
        final String version;
        final String type;
        final String classifier;

        do {
            if (dgroup == null) {
                do {
                    out.printf("%s   group id: ", iprefix);
                    out.flush();
                    group = StringUtils.trim(in.readLine());
                } while (StringUtils.isEmpty(group));
            } else {
                out.printf("%s   group id [%s]: ", iprefix, dgroup);
                out.flush();
                group = StringUtils.defaultIfBlank(StringUtils.trim(in.readLine()), dgroup);
            }
        } while (StringUtils.isEmpty(group));
        out.printf("%s   artifact id [blacklist-words]: ", iprefix);
        out.flush();
        id = StringUtils.defaultIfBlank(StringUtils.trim(in.readLine()), "blacklist-words");
        out.printf("%s   version [RELEASE]: ", iprefix);
        out.flush();
        version = StringUtils.defaultIfBlank(StringUtils.trim(in.readLine()), "RELEASE");
        out.printf("%s   type [txt]: ", iprefix);
        out.flush();
        type = StringUtils.defaultIfBlank(StringUtils.trim(in.readLine()), "txt");
        out.printf("%s   classifier []: ", iprefix);
        out.flush();
        classifier = StringUtils.trim(in.readLine());
        this.mvnInfo = group + ':' + id + ':' + version + ':' + type;

        if (StringUtils.isNotEmpty(classifier)) {
            this.mvnInfo += ':' + classifier;
        }
        this.mvnName = id + '.' + type;
    }

    /**
     * Downloads the artifact either dynamically (if already done in the past) or by prompting for
     * the maven artifact info (only at install time).
     *
     * @param settings the maven settings file or "" if using the default one
     * @param out      the output stream where to print messages to the user
     * @throws IOException if an error occurs
     */
    public void download(String settings, PrintStream out) throws IOException {
        if (file.exists()) {
            LOGGER.log(Level.FINE, "The {0} file exists.", name);
            // check if we already attempted to download it before in which case we would have the info file
            if (!infofile.exists()) {
                // not attempted before but manually dropped so keep it as is
                return;
            }
            LOGGER.log(Level.FINE, "The {0} artifact info file exists.", name);
            if (System.currentTimeMillis() - file.lastModified() < Artifact.MILLIS_PER_DAY) {
                // only re-download it every day at max
                LOGGER.log(Level.FINE,
                        "The {0} file has been in existence for less than a day; not re-downloadings.",
                        name);
                return;
            }
            readInfo();
        } else if (infofile.exists()) {
            LOGGER.log(Level.FINE, "The {0} artifact info file doesn't exists.", name);
            readInfo();
        }
        if (install && (mvnInfo
                == null)) { // prompt for the info the first time since we have nothing
            promptInfo(new BufferedReader(new InputStreamReader(System.in)), out);
        }
        if (mvnInfo != null) { // we have info to download from
            downloadUsingMaven(settings, out);
            writeInfo(); // cache the artifact info since we donwloaded it successfully
        }
    }
}
