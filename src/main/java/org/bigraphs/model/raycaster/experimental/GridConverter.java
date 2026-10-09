package org.bigraphs.model.raycaster.experimental;

import org.bigraphs.framework.core.impl.BigraphEntity;
import org.bigraphs.framework.core.impl.pure.PureBigraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Dominik Grzelak
 */
public class GridConverter {
    public GridConverter() {
    }

    /**
     * @param n       rows
     * @param m       columns
     * @param bigraph the bigrid (grid-style/location-aware bigraph
     * @return
     */
    public List<String> translateToOccupancyGrid(int n, int m, PureBigraph bigraph) {
        // Step 1: Collect all unique "parent" nodes (keys)
        Set<BigraphEntity.NodeEntity<?>> allLocales = bigraph.getNodes().stream()
                .filter(e -> e.getControl().getNamedType().stringValue().equals("Locale")).collect(Collectors.toSet());

        // Step 2: Create a grid and populate it with values
        int[][] grid = new int[n][m];

        for (BigraphEntity.NodeEntity node : allLocales) {
            int index = extractLinkIndex(node, bigraph);
            int i = index / m;
            int j = index % m;

            int value = getOccupancyValueFor(node, bigraph);
            grid[i][j] = value;
        }

        // Step 3: Convert the int grid to List<String> row-wise
        List<String> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            StringBuilder row = new StringBuilder();
            for (int j = 0; j < m; j++) {
                row.append(grid[i][j]);
            }
            result.add(row.toString());
        }

        return result;
    }

    private int extractLinkIndex(BigraphEntity.NodeEntity node, PureBigraph bigraph) {
        String linkName = bigraph.getLinkOfPoint(bigraph.getPorts(node).getFirst()).getName();
        return Integer.parseInt(linkName.replaceAll("\\D", ""));
    }

    private int getOccupancyValueFor(BigraphEntity.NodeEntity node, PureBigraph bigraph) {

        List<BigraphEntity<?>> childrenOf = bigraph.getChildrenOf(node).stream()
                .filter(e -> e.getControl().getNamedType().stringValue().equals("OccupiedBy"))
                .toList();
        if(childrenOf.isEmpty()) {
            return 0;
        } else {
            BigraphEntity<?> occupiedByNode = childrenOf.getFirst();
            if(bigraph.getChildrenOf(occupiedByNode).isEmpty()) { return 0;}
            List<BigraphEntity<?>> robots = bigraph.getChildrenOf(occupiedByNode).stream().filter(x -> x.getControl().getNamedType().stringValue().equals("Robot")).toList();
            return !robots.isEmpty() ? 1 : 0;
        }
    }
}