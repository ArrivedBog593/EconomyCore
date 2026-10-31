import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** EconomyCore's logo: a gold credit coin over a stack of coins, as 32x32 pixel art scaled up. */
public class Logo {
    static int[][] px = new int[32][32];

    static void set(int x, int y, int c) { if (x >= 0 && y >= 0 && x < 32 && y < 32) px[y][x] = 0xFF000000 | c; }

    public static void main(String[] a) throws Exception {
        // Rounded dark-teal tile with a lighter rim
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            int dx = Math.max(0, Math.max(3 - x, x - 28)), dy = Math.max(0, Math.max(3 - y, y - 28));
            if (dx * dx + dy * dy > 9) continue;
            boolean rim = x == 0 || y == 0 || x == 31 || y == 31 || dx * dx + dy * dy > 4;
            int base = rim ? 0x2E6B5E : (((x + y) % 7 == 0) ? 0x17423A : 0x1B4A41);
            set(x, y, base);
        }
        // Stack of coins, bottom right, seen edge-on
        for (int i = 0; i < 4; i++) {
            int y = 25 - i * 2;
            for (int x = 17; x <= 28; x++) {
                set(x, y, x == 17 || x == 28 ? 0x8A5A12 : 0xC9891E);
                set(x, y + 1, 0x7A4C0E);
            }
            for (int x = 18; x <= 27; x++) set(x, y - 1, i == 3 ? 0xF2C94C : 0xE0A82E);
        }
        // Big coin, upper left
        int cx = 13, cy = 13;
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            double d = Math.hypot(x - cx + 0.5, y - cy + 0.5);
            if (d > 10.3) continue;
            int c;
            if (d > 9.2) c = 0x7A4C0E;                       // outline
            else if (d > 8.0) c = (x + y) % 2 == 0 ? 0xE0A82E : 0xC9891E; // milled edge
            else if (d > 7.0) c = 0xB07818;                  // inner ring
            else c = (x - cx) + (y - cy) < -3 ? 0xF7D774 : 0xF2C94C; // face, lit from the top left
            set(x, y, c);
        }
        // The credit mark: a C with a bar through it
        int ink = 0x8A5A12;
        int[][] letter = {
            {0, 1, 1, 1, 1, 0},
            {1, 1, 0, 0, 1, 1},
            {1, 1, 0, 0, 0, 0},
            {1, 1, 0, 0, 0, 0},
            {1, 1, 0, 0, 0, 0},
            {1, 1, 0, 0, 1, 1},
            {0, 1, 1, 1, 1, 0},
        };
        for (int r = 0; r < letter.length; r++) for (int c = 0; c < 6; c++)
            if (letter[r][c] == 1) set(cx - 3 + c, cy - 3 + r, ink);
        for (int y = cy - 5; y <= cy + 5; y++) set(cx, y, ink);
        set(cx - 6, cy - 6, 0xFFF4C2); set(cx - 5, cy - 7, 0xFFF4C2); set(cx - 7, cy - 5, 0xFFF4C2); // shine
        // Sparkle
        int sx = 26, sy = 6;
        set(sx, sy, 0xFFFFFF); set(sx - 1, sy, 0xBFF5E8); set(sx + 1, sy, 0xBFF5E8); set(sx, sy - 1, 0xBFF5E8); set(sx, sy + 1, 0xBFF5E8);

        int scale = Integer.parseInt(a[0]);
        BufferedImage img = new BufferedImage(32 * scale, 32 * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32 * scale; y++) for (int x = 0; x < 32 * scale; x++) img.setRGB(x, y, px[y / scale][x / scale]);
        ImageIO.write(img, "png", new File(a[1]));
    }
}
