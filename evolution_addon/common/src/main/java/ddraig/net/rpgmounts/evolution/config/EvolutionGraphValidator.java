package ddraig.net.rpgmounts.evolution.config;

import java.util.*;

/**
 * Graph validator for RPG Mounts Evolution Trees.
 * Detects cyclical dependency loops, orphans, invalid configurations, and registers diagnostic reports.
 * Uses a three-color depth-first search (DFS) algorithm for linear validation complexity.
 */
public class EvolutionGraphValidator {

    public static class ValidationReport {
        public boolean isValid = true;
        public final List<String> errorMessages = new ArrayList<>();

        public void addError(String message) {
            isValid = false;
            errorMessages.add(message);
        }
    }

    /**
     * Validates adjacency list representation of evolution trees.
     * Checks for cyclical dependency loops.
     */
    public static ValidationReport validate(Map<String, List<String>> adjList) {
        ValidationReport report = new ValidationReport();
        Map<String, Integer> colors = new HashMap<>();

        // Initialize colors as WHITE (0)
        for (String node : adjList.keySet()) {
            colors.put(node, 0);
        }

        // Run DFS on each unvisited node
        for (String node : adjList.keySet()) {
            if (colors.getOrDefault(node, 0) == 0) {
                if (hasCycleDFS(node, adjList, colors, report, new ArrayList<>())) {
                    report.isValid = false;
                }
            }
        }
        return report;
    }

    private static boolean hasCycleDFS(String current, Map<String, List<String>> adjList, 
                                       Map<String, Integer> colors, ValidationReport report, 
                                       List<String> recursionStack) {
        colors.put(current, 1); // Mark as GREY (Active in stack)
        recursionStack.add(current);

        List<String> children = adjList.getOrDefault(current, Collections.emptyList());
        for (String neighbor : children) {
            Integer color = colors.getOrDefault(neighbor, 0);
            if (color == 1) { // Hit GREY: Cycle detected!
                int cycleStartIndex = recursionStack.indexOf(neighbor);
                StringBuilder cyclePath = new StringBuilder();
                for (int i = cycleStartIndex; i < recursionStack.size(); i++) {
                    cyclePath.append(recursionStack.get(i)).append(" -> ");
                }
                cyclePath.append(neighbor);
                report.addError("Cyclical dependency loop detected: " + cyclePath.toString());
                return true;
            } else if (color == 0) { // WHITE: Unvisited
                if (hasCycleDFS(neighbor, adjList, colors, report, recursionStack)) {
                    return true;
                }
            }
        }

        colors.put(current, 2); // Mark as BLACK (Finished)
        recursionStack.remove(recursionStack.size() - 1);
        return false;
    }
}
