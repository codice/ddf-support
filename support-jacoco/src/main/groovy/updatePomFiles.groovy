package org.codice.ddf.support

import org.apache.maven.shared.invoker.DefaultInvocationRequest
import org.apache.maven.shared.invoker.DefaultInvoker
import org.apache.maven.shared.invoker.InvocationOutputHandler

import java.text.DecimalFormat

class Globals {
    static boolean debug = false;

    static final String targetCoverage = "0.80"
    static final COVERAGE_ITEM_KEYS = ["instructions", "branches", "complexity", "lines"]
    static final POM_HEADER = """<?xml version="1.0" encoding="UTF-8"?>
<!--
  /************************************************************************
  ** Distribution Statement C.  Distribution authorized to U.S. Government
  ** Agencies and their contractors (Critical Technology)
  **
  ** DESTRUCTION NOTICE - Destroy by any method that will prevent disclosure
  ** of the contents or reconstruction of the document.
  **
  ** Warning - This document contains technical data whose export is restricted
  ** by the Arms Export Control Act (Title 22, U.S.C., Sec 2751, et seq.) or
  ** the Export Administration Act of 1979, as amended, Title 50, U.S.C.,
  ** App.2401 et seq.  Violations of these export laws are subject to severe
  ** criminal penalties. Disseminate in accordance with provisions of DoD
  ** Directive 5203.25.
  **
  ** (C) Copyright 2011 Lockheed Martin
  ** Unlimited Government Rights (FAR Subpart 27.4)
  ** Government right to use, disclose, reproduce, prepare derivative works,
  ** distribute copies to the public, and perform and display publicly, in any
  ** manner and for any purpose, and to have or permit others to do so.
  **
  ************************************************************************/
  -->
"""
    static final JACOCO_CONFIG_TEMPLATE = """
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>default-check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <haltOnFailure>true</haltOnFailure>
                        <rules>
                            <rule>
                                <element>BUNDLE</element>
                                <limits>
                                    <limit>
                                        <counter>INSTRUCTION</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>{instructions-ratio}</minimum>
                                    </limit>
                                    <limit>
                                        <counter>BRANCH</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>{branches-ratio}</minimum>
                                    </limit>
                                    <limit>
                                        <counter>COMPLEXITY</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>{complexity-ratio}</minimum>
                                    </limit>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>{lines-ratio}</minimum>
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        """
}

class OutputHandler implements InvocationOutputHandler {
    private final Map<String, String> ratios = new HashMap<String, String>()

    public OutputHandler() {
        // Set the default coverage values in case some of them are already above the target
        Globals.COVERAGE_ITEM_KEYS.each() {
            ratios.put(it, Globals.targetCoverage)
        }
    }

    public void consumeLine(String line) {
        if (Globals.debug) {
            println line
        }

        if (line.contains("[WARNING] Rule violated for bundle ")) {
            def matcher = (line =~ /^.* ([a-z]+) covered ratio is ([0-9.]+).*/)
            def ratioName = matcher[0][1]
            def ratioValue = matcher[0][2]
            // printf("***** Covered [%s] ratio: [%f]\n", ratioName, ratioValue)
            ratios.put(ratioName, ratioValue)
        }
    }

    public Map<String, String> getRatios() {
        return ratios;
    }
}

class JaCoCoPomUpdater {

    public void process(String projectsRoot) {
        println "Processing Maven projects in ${projectsRoot}"

        new File(projectsRoot).eachFileRecurse() {
            if (it.name.equals("pom.xml")) {
                processPomFile(it)
            }
        }
    }

    private void processPomFile(File pomFile) {
        def pom = new XmlParser().parse(pomFile)

        if (!isJaCoCoAlreadyConfigured(pom)) {
            println "${pomFile.path}: jacoco-maven-plugin not found. Configuring."

            try {
                Map<String, Float> extractedRatios = extractCoverageRatiosFromPomFile(pomFile)
                def output = generateJaCoCoConfiguration(extractedRatios)

                if (pom.build == null || pom.build.plugins == null || pom.build.plugins[0] == null) {
                    println "${pomFile.path}: No <build> or <plugins> elements found. Skipping."
                    return
                }

                def jaCoCoPlugInNode = new XmlParser().parseText(output)
                pom.build.plugins[0].append(jaCoCoPlugInNode)

                updatePomFile(pomFile, pom)
            }
            catch (RuntimeException e) {
                println "${pomFile.path} failed. Skipping."
                e.printStackTrace()
            }
        } else {
            println "${pomFile.path}: jacoco-maven-plugin already configured. Skipping."
        }
    }

    private boolean isJaCoCoAlreadyConfigured(Node pom) {
        def configured = false;

        pom.build.plugins.plugin.each() {
            if (it.artifactId.text().equals("jacoco-maven-plugin")) {
                configured = true
            }
        }

        return configured
    }

    private Map<String, Float> extractCoverageRatiosFromPomFile(File pomFile) {
        def request = new DefaultInvocationRequest()
        request.setPomFile(pomFile)
        request.setGoals(Collections.singletonList("install"))
        request.setRecursive(false)

        def outputHandler = new OutputHandler()
        request.setOutputHandler(outputHandler)

        def invoker = new DefaultInvoker()
        def result = invoker.execute(request)

        if (result.getExitCode() != 0 || result.executionException != null) {
            throw new IllegalStateException("Build failed.");
        }

        return outputHandler.getRatios()
    }

    private generateJaCoCoConfiguration(Map<String, String> extractedRatios) {
        def formatter = new DecimalFormat("#.##")

        def output = Globals.JACOCO_CONFIG_TEMPLATE

        extractedRatios.each() {
            def ratio = new Double(it.value)
            ratio = Math.max(ratio - 0.05, 0.0)
            output = output.replace("{" + it.key + "-ratio}", formatter.format(ratio))
        }

        return output
    }

    private void updatePomFile(File pomFile, Node pom) {
        def writer = new PrintWriter(pomFile)
        def xmlPrinter = new XmlNodePrinter(writer, "    ")
        xmlPrinter.setPreserveWhitespace(true)

        writer.print(Globals.POM_HEADER)
        xmlPrinter.print(pom)
    }
}

def usage = "Usage: mvn groovy:execute -DprojectsRoot=<maven project root directory> [-Ddebug]"
def projectsRoot = System.getProperty("projectsRoot")

if (projectsRoot == null) {
    println "projectsRoot system property missing."
    println usage
    System.exit(1)
}

Globals.debug = System.getProperty("debug") == null ? false : true

def updater = new JaCoCoPomUpdater()
updater.process(projectsRoot)
