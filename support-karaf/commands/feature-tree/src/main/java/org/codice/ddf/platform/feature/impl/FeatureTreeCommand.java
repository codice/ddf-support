package org.codice.ddf.platform.feature.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.karaf.features.Dependency;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;

@Service
@Command(scope = "feature", name = "tree")
public class FeatureTreeCommand implements Action {
    @Argument(name = "Feature name", description = "Name of the feature to use as root of the feature tree.", required = true)
    private String rootFeatureName = null;

    @Option(name = "--depth", description = "How many levels deep the tree should be.", aliases = {
            "-d"})
    private int maxDepth = 100;

    @Option(name = "--no-duplicates", description = "Do not repeat feature sub-trees that have already been displayed.", aliases = {
            "-n"})
    private boolean noDuplicates = false;

    @Option(name = "--include-repo", description = "Include only features coming from the repositories matching this regular expression.", aliases = {
            "-i"})
    private String repoFilter = ".*(ddf|dib|alliance).*";

    @Option(name = "--line-numbers", description = "Displays line numbers and cross-references", aliases = {
            "-l"})
    private boolean printLineNumbers = false;

    private static int lineNumber = 1;

    @Reference
    FeaturesService featuresService;

    private Map<String, Integer> subTreesAlreadyVisited = new HashMap<>();

    @Override
    public Object execute() throws Exception {
        lineNumber = 1;
        printDependencies(rootFeatureName, 0);
        return null;
    }

    private void printDependencies(String name, int depth) {
        if (depth > maxDepth) {
            return;
        }

        Feature feature;

        try {
            feature = featuresService.getFeature(name);
        } catch (Exception e) {
            return;
        }

        if (feature == null || !feature.getRepositoryUrl()
                .matches(repoFilter)) {
            return;
        }

        if (noDuplicates && subTreesAlreadyVisited.containsKey(name)) {
            printDependency(name, depth, true);
            lineNumber++;
            return;
        }

        printDependency(name, depth, false);
        subTreesAlreadyVisited.put(name, lineNumber++);

        List<Dependency> dependencies = feature.getDependencies();

        for (Dependency dependency : dependencies) {
            printDependencies(dependency.getName(), depth + 1);
        }
    }

    private void printDependency(String name, int depth, boolean alreadyVisited) {
        if (printLineNumbers) {
            System.out.print(String.format("%4d - ", lineNumber));
        }

        System.out.print(String.format("%s%s", StringUtils.repeat(" ", depth * 2), name));

        if (alreadyVisited) {
            if (printLineNumbers) {
                System.out.print(String.format(" -> %d", subTreesAlreadyVisited.get(name)));
            } else {
                System.out.print(" *");
            }
        }

        System.out.println();
    }
}
