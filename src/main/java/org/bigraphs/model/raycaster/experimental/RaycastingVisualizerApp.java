package org.bigraphs.model.raycaster.experimental;

import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Interactive raycasting-based visualizer for spatial system states represented
 * as grid-based maps.
 * <p>
 * The raycasting implementation is adapted and extended from
 * <a href="https://github.com/3DSage/OpenGL-Raycaster_v1">OpenGL-Raycaster_v1</a>
 * by 3DSage.
 *
 * @author Dominik Grzelak
 */
public class RaycastingVisualizerApp {

    public static void main(String[] args) {
        try {
            new RaycastingVisualizerApp().run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class RayHit {
        public float rayX, rayY;     // where the ray hit in 2D
        public float distance;       // corrected distance
        public boolean verticalHit;  // true if vertical wall hit
        public float[] colorDark = {0, 0.6f, 0}; // vertical hit
        public float[] colorLight = {0, 0.8f, 0}; // horizontal hit
    }

    static Random rand = new Random();

    // Map
    int currentMapIndex = 0;
    List<MapLoader.MapState> mapsLoaded = new ArrayList<>();
    final int mapX = 4, // Number of columns in the map grid (horizontal size in cells)
            mapY = 2, // Number of rows in the map grid (vertical size in cells)
            mapS = 64; // Size of each cell in pixels (both width and height)

    // Window
    private long window;
    private final int
            HEIGHT = 512, // Height of the entire screen/window in pixels
            WIDTH = 1024; // Width of the entire screen/window in pixels
    // Camera (Observer)
    private final int rayCount = 60;
    float px = 150, py = 400, pdx, pdy;
    float pa = 90;

    int[] currentMap = {
    };

    int[] secondaryMap = {
            0, 0, 0, 0,
            0, 0, 0, 0
    };

    // "manual mode"
    int[][] initMap = {
            {0, 0, 1},
            {0, 1, 0},
            {0, 1, 0},
    };


    public void run() throws IOException {
        init();
        initState();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    protected void initState() throws IOException {
//        Set<String> goodStates = Set.of();
        Set<String> goodStates = new HashSet<>(Arrays.asList(
                "a_1", "a_3", "a_5", "a_7", "a_11", "a_13",
                "a_15", "a_19", "a_23", "a_29"
        ));
        mapsLoaded = MapLoader.loadMaps("./assets/data/ssr-2x4/", goodStates, mapY, mapX);
        if (mapsLoaded.isEmpty()) {
            throw new RuntimeException("No maps loaded");
        }
        loadMapIntoWorld(mapsLoaded.get(currentMapIndex));
    }

    // Manual mode: Update initMap and its dimensions (mapX and mapY).
    // Optionally, adjust secondaryMap to match the grid.
//    protected void initState() throws IOException {
//        MapState map = new MapState(initMap);
//        mapsLoaded = Arrays.asList(map);
//        if (mapsLoaded.isEmpty()) {
//            throw new RuntimeException("No maps loaded");
//        }
//        loadMapIntoWorld(mapsLoaded.get(currentMapIndex));
//    }

    protected void init() {
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);

        if (!glfwInit())
            throw new IllegalStateException("GLFW initialization failed");

        window = glfwCreateWindow(WIDTH, HEIGHT, "Bigrid Raycaster Demo", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create window");

        glfwMakeContextCurrent(window);
        glfwSetKeyCallback(window, this::handleInput);
        glfwSwapInterval(1);
        GL.createCapabilities();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        updateDirectionVector();
    }

    protected void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            drawBackground();
            drawShadedFloor(WIDTH, HEIGHT);

            drawMap2D();
            drawOverlay2D();
            drawObserver2D();

            RayHit[] hits = castRays(px, py, pa, rayCount, 60);
            drawRaysOnMap2D(hits, px, py); // top-down map
            drawRays3D(hits);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    protected void handleInput(long win, int key, int scancode, int action, int mods) {
        if (action == GLFW_PRESS || action == GLFW_REPEAT) {
            if (key == GLFW_KEY_A) {
                pa = fixAngleF(pa + 5);
                updateDirectionVector();
            }
            if (key == GLFW_KEY_D) {
                pa = fixAngleF(pa - 5);
                updateDirectionVector();
            }
            if (key == GLFW_KEY_W) {
                px += pdx * 5;
                py += pdy * 5;
            }
            if (key == GLFW_KEY_S) {
                px -= pdx * 5;
                py -= pdy * 5;
            }
            if (key == GLFW_KEY_E) {
                flipRandomMapCell(mapX, mapY, currentMap);
            }
            if (key == GLFW_KEY_N) {// Next map
                currentMapIndex = (currentMapIndex + 1) % mapsLoaded.size();
                System.out.println(currentMapIndex);
                loadMapIntoWorld(mapsLoaded.get(currentMapIndex));
            }

        }
    }

    protected void loadMapIntoWorld(MapLoader.MapState mapState) {
        currentMap = Arrays.stream(mapState.getGrid())
                .flatMapToInt(Arrays::stream)
                .toArray();
    }

    protected void updateDirectionVector() {
        double rad = Math.toRadians(pa);
        pdx = (float) Math.cos(rad);
        pdy = (float) -Math.sin(rad);
    }

    protected void drawMap2D() {
        for (int y = 0; y < mapY; y++) {
            for (int x = 0; x < mapX; x++) {
                if (currentMap[y * mapX + x] == 1) glColor3f(1, 1, 1);
                else glColor3f(0, 0, 0);

                int xo = x * mapS;
                int yo = y * mapS;

                glBegin(GL_QUADS);
                glVertex2i(xo + 1, yo + 1);
                glVertex2i(xo + 1, yo + mapS - 1);
                glVertex2i(xo + mapS - 1, yo + mapS - 1);
                glVertex2i(xo + mapS - 1, yo + 1);
                glEnd();
            }
        }
    }

    protected void drawObserver2D() {
        glColor3f(1, 1, 0);
        glPointSize(8);
        glLineWidth(4);

        glBegin(GL_POINTS);
        glVertex2f(px, py);
        glEnd();

        glBegin(GL_LINES);
        glVertex2f(px, py);
        glVertex2f(px + pdx * 20, py + pdy * 20);
        glEnd();
    }

    protected static void flipRandomMapCell(int MAP_WIDTH, int MAP_HEIGHT, int[] map) {
        while (true) {
            int x = rand.nextInt(MAP_WIDTH);
            int y = rand.nextInt(MAP_HEIGHT);
            int index = y * MAP_WIDTH + x;

            // Avoid flipping the boundary of the grid
            if (x == 0 || x == MAP_WIDTH - 1 || y == 0 || y == MAP_HEIGHT - 1) continue;

            map[index] = (map[index] == 0) ? 1 : 0;
            System.out.println("Flipped cell at (" + x + "," + y + ") to " + map[index]);
            break;
        }
    }

    /**
     *
     * @param px
     * @param py
     * @param pa
     * @param rayCount
     * @param fov      in degrees
     * @return
     */
    protected RayHit[] castRays(float px, float py, float pa, int rayCount, float fov) {
        RayHit[] hits = new RayHit[rayCount];
        float ra = pa + (fov / 2); // start from leftmost angle
        ra = fixAngleF(ra);

        for (int r = 0; r < rayCount; r++) {
            float rayAngle = fixAngleF(ra);
            float sinA = (float) Math.sin(Math.toRadians(rayAngle));
            float cosA = (float) Math.cos(Math.toRadians(rayAngle));
            float tanA = (float) Math.tan(Math.toRadians(rayAngle));

            float disV = 1e6f, vx = px, vy = py;
            float disH = 1e6f, hx = px, hy = py;

            hits[r] = new RayHit();

            // Vertical check
            {
                float rx, ry, xo, yo;
                int dof = 0;
                if (cosA > 0.001) {
                    rx = ((int) (px / mapS) * mapS + mapS);
                    ry = (px - rx) * tanA + py;
                    xo = mapS;
                    yo = -xo * tanA;
                } else if (cosA < -0.001) {
                    rx = ((int) (px / mapS) * mapS - 0.0001f);
                    ry = (px - rx) * tanA + py;
                    xo = -mapS;
                    yo = -xo * tanA;
                } else {
                    rx = px;
                    ry = py;
                    dof = 8;
                    xo = yo = 0;
                }

                while (dof < 8) {
                    int mx = (int) (rx) / mapS;
                    int my = (int) (ry) / mapS;
                    if (mx >= 0 && my >= 0 && mx < mapX && my < mapY && currentMap[my * mapX + mx] >= 1) {
                        vx = rx;
                        vy = ry;
                        disV = distance(px, py, rx, ry, rayAngle);
                        if (currentMap[my * mapX + mx] == 2) {
                            hits[r].colorDark = new float[]{1f, 0.5f, 0.2f};
                            hits[r].colorLight = new float[]{1f, 0.2f, 0.2f};
                        }
                        break;
                    }
                    rx += xo;
                    ry += yo;
                    dof++;
                }
            }

            // Horizontal check
            {
                float rx, ry, xo, yo;
                int dof = 0;
                if (sinA > 0.001) {
                    ry = ((int) (py / mapS) * mapS - 0.0001f);
                    rx = (py - ry) / tanA + px;
                    yo = -mapS;
                    xo = -yo / tanA;
                } else if (sinA < -0.001) {
                    ry = ((int) (py / mapS) * mapS + mapS);
                    rx = (py - ry) / tanA + px;
                    yo = mapS;
                    xo = -yo / tanA;
                } else {
                    rx = px;
                    ry = py;
                    dof = 8;
                    xo = yo = 0;
                }

                while (dof < 8) {
                    int mx = (int) (rx) / mapS;
                    int my = (int) (ry) / mapS;
                    if (mx >= 0 && my >= 0 && mx < mapX && my < mapY && currentMap[my * mapX + mx] >= 1) {
                        hx = rx;
                        hy = ry;
                        disH = distance(px, py, rx, ry, rayAngle);
                        if (currentMap[my * mapX + mx] == 2) {
                            hits[r].colorDark = new float[]{1f, 0.5f, 0.2f};
                            hits[r].colorLight = new float[]{1f, 0.2f, 0.2f};
                        }
                        break;
                    }
                    rx += xo;
                    ry += yo;
                    dof++;
                }
            }

            float hitX, hitY, dist;
            boolean vertical = disV < disH;
            if (vertical) {
                hitX = vx;
                hitY = vy;
                dist = disV;
            } else {
                hitX = hx;
                hitY = hy;
                dist = disH;
            }

            // Fix fisheye
            float corrected = dist * (float) Math.cos(Math.toRadians(pa - rayAngle));

            hits[r].rayX = hitX;
            hits[r].rayY = hitY;
            hits[r].distance = corrected;
            hits[r].verticalHit = vertical;

            ra -= fov / rayCount;
        }

        return hits;
    }

    protected void drawRaysOnMap2D(RayHit[] hits, float px, float py) {
        glColor3f(0, 1, 0);
        glLineWidth(2);
        for (RayHit ray : hits) {
            glBegin(GL_LINES);
            glVertex2f(px, py);
            glVertex2f(ray.rayX, ray.rayY);
            glEnd();
        }
    }

    protected void drawRays3D(RayHit[] hits) {

        int screenX = WIDTH / 2;
        int screenHeight = HEIGHT;
        int screenMiddle = screenX / 2;

        int maxRays = hits.length;
        int rayWidth = (int) Math.floor((double) (WIDTH / 2) / maxRays);

        for (int r = 0; r < maxRays; r++) {
            float dist = hits[r].distance;
            int lineHeight = (int) ((mapS * screenHeight) / dist);
            if (lineHeight > screenHeight) lineHeight = screenHeight;
            int lineOffset = screenMiddle - (lineHeight / 2);

            if (hits[r].verticalHit) glColor3f(0, 0.6f, 0);
            else glColor3f(0, 0.8f, 0);

            if (hits[r].verticalHit) glColor3f(hits[r].colorDark[0], hits[r].colorDark[1], hits[r].colorDark[2]);
            else glColor3f(hits[r].colorLight[0], hits[r].colorLight[1], hits[r].colorLight[2]);

            glLineWidth(rayWidth);
            glBegin(GL_LINES);
            glVertex2i(screenX + r * rayWidth + rayWidth, lineOffset);
            glVertex2i(screenX + r * rayWidth + rayWidth, lineOffset + lineHeight);
            glEnd();

            int cellX = (int) (hits[r].rayX) / mapS;
            int cellY = (int) (hits[r].rayY) / mapS;
            int index = cellY * mapX + cellX;
            drawAdditionalLayer(r, rayWidth, index, screenX, lineOffset, lineHeight);
        }
    }

    protected void drawAdditionalLayer(int r, int w, int index, int screenX, int lineOffset, int lineHeight) {
        if (index >= 0 && index < secondaryMap.length) {

            float glow = 1.0f; // could animate or pulse this
            if (secondaryMap[index] == 1) {
                glColor3f(glow, 0.4f, 0.4f); // tinted overlay
            }
            if (secondaryMap[index] == 2) {
                glColor3f(0.4f, glow, 0.4f);
            }
            if (secondaryMap[index] == 3) {
                glColor3f(0.4f, 0.4f, glow);
            }

            // draw a thinner vertical bar over/next to wall
            glLineWidth(2f);
            glBegin(GL_LINES);
            glVertex2i(screenX + r * w, lineOffset);
            glVertex2i(screenX + r * w, lineOffset + lineHeight);
            glEnd();
        }
    }

    protected void drawBackground() {
        int screenX = WIDTH - WIDTH / 2;
        int screenHeight = HEIGHT;
        int screenMiddle = WIDTH / 2;

        // Draw sky (top half)
        glColor3f(0.3f, 0.5f, 0.9f);
        glBegin(GL_QUADS);
        glVertex2i(screenX, 0);               // top-left
        glVertex2i(WIDTH, 0);              // top-right
        glVertex2i(WIDTH, screenHeight / 2); // bottom-right
        glVertex2i(screenMiddle, screenHeight / 2); // bottom-left
        glEnd();

        // Draw floor (bottom half)
        glColor3f(0.4f, 0.4f, 0.4f);
        glBegin(GL_QUADS);
        glVertex2i(screenX, screenHeight / 2);     // top-left
        glVertex2i(WIDTH, screenHeight / 2);    // top-right
        glVertex2i(WIDTH, screenHeight);        // bottom-right
        glVertex2i(screenMiddle, screenHeight);        // bottom-left
        glEnd();
    }

    protected void drawShadedFloor(int screenWidth, int screenHeight) {
        int horizon = screenHeight / 2;

        for (int y = horizon; y < screenHeight; y++) {
            float depth = (float) (y - horizon) / (screenHeight - horizon);
            float brightness = 1.0f - depth;
            brightness = Math.max(0.21f, brightness); // avoid full black
            float alpha = 0.4f;
            glColor3f(alpha * brightness, alpha * brightness, alpha * brightness);
            glBegin(GL_LINES);
            glVertex2i(screenWidth / 2, y);
            glVertex2i(screenWidth, y);
            glEnd();
        }
    }

    protected void drawOverlay2D() {
        if (secondaryMap == null || secondaryMap.length == 0) {
            return;
        }
        for (int y = 0; y < mapY; y++) {
            for (int x = 0; x < mapX; x++) {
                int i = y * mapX + x;
                if (i >= secondaryMap.length) continue;
                if (secondaryMap[i] == 0) {
                    continue;
                }
                int cellX = x * mapS;
                int cellY = y * mapS;

                if (secondaryMap[i] == 1) {
                    glColor3f(1.0f, 0.2f, 0.2f); // Red highlight or glow
//                    float pulse = 0.5f + 0.5f * (float)Math.sin(System.currentTimeMillis() * 0.005);
//                    glColor3f(pulse, 0.1f, 0.1f);
                }
                if (secondaryMap[i] == 2) {
                    glColor3f(0.2f, 1.0f, 0.2f);
                }
                if (secondaryMap[i] == 3) {
                    glColor3f(0.2f, 0.2f, 1.0f);
                }

                glBegin(GL_QUADS);
                glVertex2i(cellX + 8, cellY + 8);
                glVertex2i(cellX + 8, cellY + mapS - 8);
                glVertex2i(cellX + mapS - 8, cellY + mapS - 8);
                glVertex2i(cellX + mapS - 8, cellY + 8);
                glEnd();
            }
        }
    }

    protected static float distance(float ax, float ay, float bx, float by, float angle) {
        return (float) (
                Math.cos(Math.toRadians(angle)) * (bx - ax) -
                        Math.sin(Math.toRadians(angle)) * (by - ay)
        );
    }

    protected static float fixAngleF(float a) {
        if (a < 0) return a + 360;
        if (a >= 360) return a - 360;
        return a;
    }
}
