/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
package org.codice.ddf.support

import org.apache.maven.shared.invoker.DefaultInvocationRequest
import org.apache.maven.shared.invoker.DefaultInvoker
import org.apache.maven.shared.invoker.InvocationOutputHandler
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory
import java.text.DecimalFormat

/*
 * This Groovy script is used to add JaCoCo () code coverage tool configuration to one or more pom.xml files.
 *
 * To use the tool, execute the following Maven command from the support-jacoco directory:
 *   > mvn groovy:execute -DprojectsRoot=<Maven project root directory>
 *
 * An optional -Ddebug argument can be provided to turn on debug information.
 */

/**
 * Script global variables and constants
 */
class Globals {
    static boolean debug = false;

    static final String targetCoverage = "0.80"
    static final COVERAGE_ITEM_KEYS = ["instructions", "branches", "complexity", "lines"]
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

/**
 * Class used to capture the output of the maven command and extract the different code coverage values that will be
 * used to generated the JaCoCo configuration.
 */
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

            if (Globals.debug) {
                println "***** Covered ${ratioName} ratio: ${ratioValue}"
            }

            ratios.put(ratioName, ratioValue)
        }
    }

    public Map<String, String> getRatios() {
        return ratios;
    }
}

/**
 * Main class used to update the pom files with the proper JaCoCo configuration.
 */
class JaCoCoPomUpdater {

    private XPathFactory xPathFactory = XPathFactory.newInstance();
    private XPath xpath = xPathFactory.newXPath();
    private XPathExpression jaCoCoPlugInXPath = xpath.compile('/project/build/plugins/plugin/artifactId[text()="jacoco-maven-plugin"]');
    private XPathExpression plugInsXPath = xpath.compile('/project/build/plugins');
    private DocumentBuilderFactory factory;

    public JaCoCoPomUpdater() {
        factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringComments(false);
        factory.setValidating(false);
    }
    /**
     * Recursively processes all the pom.xml files found under the root directory provided. This method will
     * automatically skip the pom.xml files that already contain a JaCoCo configuration element. The files that don't
     * will be updated with the proper JaCoCo code coverage values.
     *
     * @param projectsRoot root direction to process
     */
    public void process(String projectsRoot) {
        println "Processing Maven projects in ${projectsRoot}"

        new File(projectsRoot).eachFileRecurse() {
            if (it.name.equals("pom.xml")) {
                processPomFile(it)
            }
        }
    }

    private void processPomFile(File pomFile) {
        def builder = factory.newDocumentBuilder();
        def document = builder.parse(pomFile);

        Node plugInsNode = (Node) plugInsXPath.evaluate(document, XPathConstants.NODE)

        if (plugInsNode == null) {
            println "${pomFile.path}: No <build> or <plugins> elements found. Skipping."
            return
        }

        if (isJaCoCoAlreadyConfigured(document)) {
            println "${pomFile.path}: jacoco-maven-plugin already configured. Skipping."
            return
        }

        println "${pomFile.path}: jacoco-maven-pluginnot found. Configuring."

        try {
            Map<String, Float> extractedRatios = extractCoverageRatiosFromPomFile(pomFile)
            def jaCoCoPlugInNode = generateJaCoCoConfiguration(extractedRatios)
            plugInsNode.appendChild(document.importNode(jaCoCoPlugInNode, true))

            updatePomFile(pomFile, document)
        }
        catch (RuntimeException e) {
            println "${pomFile.path} failed. Skipping."
            e.printStackTrace()
        }
    }

    private boolean isJaCoCoAlreadyConfigured(Document pom) {
        return (jaCoCoPlugInXPath.evaluate(pom, XPathConstants.NODE) != null)
    }

    private boolean buildPluginElementExist(Document pom) {
        return plugInsXPath.evaluate(pom, XPathConstants.NODE) != null
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

        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(output.replaceAll(/\s+/, '')));
        return builder.parse(is).getDocumentElement()
    }

    private void updatePomFile(File pomFile, Document pom) {
        def fileWriter = new FileWriter(pomFile)
        def xmlOutput = new StreamResult(fileWriter)
        def transformerFactory = TransformerFactory.newInstance()
        def transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.METHOD, "xml")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.transform(new DOMSource(pom), xmlOutput)
    }
}

/*
 * Script main function
 */

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
