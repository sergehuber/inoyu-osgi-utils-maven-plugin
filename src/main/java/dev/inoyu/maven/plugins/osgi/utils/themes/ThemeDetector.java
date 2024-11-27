package dev.inoyu.maven.plugins.osgi.utils.themes;

import java.io.File;
import java.util.Scanner;

public class ThemeDetector {

    public static String detectOSTheme() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac")) {
            return isMacDarkMode() ? "dark" : "light";
        } else if (os.contains("win")) {
            return isWindowsDarkMode() ? "dark" : "light";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return isLinuxDarkMode() ? "dark" : "light";
        }

        return "light"; // Default to light for unsupported OS
    }

    private static boolean isMacDarkMode() {
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
            process.waitFor();
            String result = new String(process.getInputStream().readAllBytes()).trim();
            return "Dark".equalsIgnoreCase(result);
        } catch (Exception e) {
            return false; // Default to light mode on error
        }
    }

    private static boolean isWindowsDarkMode() {
        try {
            Process process = new ProcessBuilder("reg", "query",
                    "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme").start();
            process.waitFor();
            String result = new String(process.getInputStream().readAllBytes()).trim();
            return result.contains("0x0"); // Dark mode
        } catch (Exception e) {
            return false; // Default to light mode on error
        }
    }

    private static boolean isLinuxDarkMode() {
        if (isGnomeDarkMode()) {
            return true;
        }

        if (isKdeDarkMode()) {
            return true;
        }

        return false; // Default to light mode for unsupported environments
    }

    private static boolean isGnomeDarkMode() {
        try {
            Process process = new ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").start();
            process.waitFor();
            String result = new String(process.getInputStream().readAllBytes()).trim();
            return result.contains("prefer-dark");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isKdeDarkMode() {
        File kdeglobals = new File(System.getProperty("user.home") + "/.config/kdeglobals");
        if (!kdeglobals.exists()) {
            return false; // Default to light mode if file doesn't exist
        }

        try (Scanner scanner = new Scanner(kdeglobals)) {
            boolean inGeneralSection = false;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                // Detect [General] section
                if (line.equals("[General]")) {
                    inGeneralSection = true;
                    continue;
                }

                // Exit [General] section
                if (line.startsWith("[") && !line.equals("[General]")) {
                    inGeneralSection = false;
                }

                // Check for ColorScheme key
                if (inGeneralSection && line.startsWith("ColorScheme=")) {
                    String colorScheme = line.split("=")[1].trim();
                    return colorScheme.toLowerCase().contains("dark");
                }
            }
        } catch (Exception e) {
            // Handle read errors (e.g., file permissions)
            return false;
        }

        return false; // Default to light mode if no match found
    }
}
