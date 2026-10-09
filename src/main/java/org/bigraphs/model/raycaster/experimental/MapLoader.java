package org.bigraphs.model.raycaster.experimental;

import org.bigraphs.framework.core.BigraphEntityType;
import org.bigraphs.framework.core.BigraphFileModelManagement;
import org.bigraphs.framework.core.Control;
import org.bigraphs.framework.core.impl.BigraphEntity;
import org.bigraphs.framework.core.impl.pure.PureBigraph;
import org.bigraphs.framework.core.impl.signature.DynamicControl;
import org.bigraphs.framework.core.impl.signature.DynamicSignature;
import org.bigraphs.framework.core.utils.BigraphUtil;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.*;

import static org.bigraphs.framework.core.factory.BigraphFactory.createOrGetSignature;

/**
 * @author Dominik Grzelak
 */
public class MapLoader {

    private static final String LOCALE_TYPE = "Locale";
    private static final String ROUTE_TYPE = "Route";

    public static List<MapState> loadMaps(String directoryPath, Set<String> allowedStates, int rows, int cols) throws IOException {
        List<EObject> eObjects = BigraphFileModelManagement.Load.signatureInstanceModel(
                Paths.get(directoryPath, "signatureMetaModel.ecore").toString(),
                Paths.get(directoryPath, "sig.xmi").toString()
        );
        DynamicSignature sig = createOrGetSignature(eObjects.getFirst());
        System.out.println(sig.getControls());
        try (Stream<Path> paths = Files.list(Paths.get(directoryPath))) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("a_\\d+\\.xmi"))
                    .filter(path -> {
                        if (allowedStates.isEmpty()) {
                            return true;
                        }
                        String name = path.getFileName().toString().replace(".xmi", "");
                        return allowedStates.contains(name);
                    })
                    .sorted(Comparator.comparingInt(path -> extractNumber(path.getFileName().toString())))
                    .map(x -> loadOccupancyGridFromBigrid(x, sig, rows, cols))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    private static MapState loadOccupancyGridFromBigrid(Path path, DynamicSignature sig, int rows, int cols) {
        try {
//            BigraphFileModelManagement.VALIDATE = false;
            String mmFile = path.getParent().resolve("bigraphMetaModel.ecore").toString();
            String instFile = path.toString();
            System.out.println("Processing file: " + path.getFileName());

            EPackage ePackage = BigraphFileModelManagement.Load.bigraphMetaModel(mmFile, false);

            EPackage.Registry.INSTANCE.put(ePackage.getNsURI(), ePackage);
            System.out.println("Loaded metamodel");

            List<EObject> eObjects = BigraphFileModelManagement.Load.bigraphInstanceModel(
                    instFile
            );
            PureBigraph bigraph = BigraphUtil.toBigraph(ePackage, eObjects.getFirst(), sig);
            List<String> lines = translateToOccupancyGrid(bigraph, rows, cols);
            return new MapState(lines);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static List<String> translateToOccupancyGrid(PureBigraph bigraph, int rows, int cols) {
        return new GridConverter().translateToOccupancyGrid(rows, cols, bigraph);
    }

    /**
     * Get all Locale-typed nodes in the bigrid.
     */
    private static List<BigraphEntity.NodeEntity<DynamicControl>> getLocales(PureBigraph bigrid) {
        List<BigraphEntity.NodeEntity<DynamicControl>> locales = new ArrayList<>();
        for (BigraphEntity.NodeEntity<DynamicControl> node : bigrid.getNodes()) {
            if (node.getControl().getNamedType().stringValue().equals(LOCALE_TYPE)) {
                locales.add(node);
            }
        }
        return locales;
    }

    private static Map<BigraphEntity.NodeEntity<DynamicControl>, List<BigraphEntity.NodeEntity<DynamicControl>>> buildAdjacencyMap(PureBigraph bigrid) {
        Map<BigraphEntity.NodeEntity<DynamicControl>, List<BigraphEntity.NodeEntity<DynamicControl>>> adjacencyMap = new HashMap<>();
        List<BigraphEntity.NodeEntity<DynamicControl>> locales = getLocales(bigrid);

        for (BigraphEntity.NodeEntity<DynamicControl> locale : locales) {
            adjacencyMap.put(locale, getConnectedLocales(bigrid, locale));
        }

        return adjacencyMap;
    }

    /**
     * Get all Locale nodes connected to the given Locale node via Route nodes and outer names.
     */
    private static List<BigraphEntity.NodeEntity<DynamicControl>> getConnectedLocales(
            PureBigraph bigrid, BigraphEntity.NodeEntity<DynamicControl> locale) {

        List<BigraphEntity.NodeEntity<DynamicControl>> connectedLocales = new ArrayList<>();

        // Get all Route nodes nested inside the current Locale node
        for (BigraphEntity<?> route : bigrid.getChildrenOf(locale)) {
            if (BigraphEntityType.isNode(route) && route.getControl().getNamedType().stringValue().equals(ROUTE_TYPE)) {
                // Find the outer name this Route node links to
                for (BigraphEntity.Link outerName : bigrid.getIncidentLinksOf((BigraphEntity.NodeEntity<? extends Control<?, ?>>) route)) {
                    // Find all Locale nodes that also link to this outer name
                    for (BigraphEntity.NodeEntity<DynamicControl> otherLocale : getLocales(bigrid)) {
                        if (!otherLocale.equals(locale) && bigrid.getIncidentLinksOf(otherLocale).contains(outerName)) {
                            connectedLocales.add(otherLocale);
                        }
                    }
                }
            }
        }
        return connectedLocales;
    }

    private static int extractNumber(String filename) {
        Matcher matcher = Pattern.compile("a_(\\d+)\\.xmi").matcher(filename);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            throw new IllegalArgumentException("Invalid file name format: " + filename);
        }
    }

    public static class MapState {
        private final int[][] grid;

        public MapState(List<String> lines) {
            int rows = lines.size();
            int cols = lines.get(0).length();
            grid = new int[rows][cols];
            for (int y = 0; y < rows; y++) {
                String line = lines.get(y);
                for (int x = 0; x < cols; x++) {
                    grid[y][x] = Character.getNumericValue(line.charAt(x));
                }
            }
        }

        public MapState(int[][] grid) {
            this.grid = grid;
        }

        public int[][] getGrid() {
            return grid;
        }

        public int getTile(int x, int y) {
            return grid[y][x];
        }

        public int getWidth() {
            return grid[0].length;
        }

        public int getHeight() {
            return grid.length;
        }
    }
}
