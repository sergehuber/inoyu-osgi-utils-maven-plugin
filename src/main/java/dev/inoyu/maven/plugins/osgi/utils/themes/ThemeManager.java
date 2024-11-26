package dev.inoyu.maven.plugins.osgi.utils.themes;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.util.EnumMap;
import java.util.Map;

public class ThemeManager {

    public enum Role {
        HEADER,
        CLAUSE,
        DIRECTIVE,
        ATTRIBUTE,
        CONTEXT,
        DETAIL,
        DEPENDENCY,
        ERROR
    }

    private static final Map<Role, Ansi.Color[]> THEMES = new EnumMap<>(Role.class);

    private static boolean isDarkMode = true; // Default to dark mode

    static {
        // Define dark mode and light mode colors for each role
        THEMES.put(Role.HEADER, new Ansi.Color[]{Ansi.Color.YELLOW, Ansi.Color.BLUE});
        THEMES.put(Role.CLAUSE, new Ansi.Color[]{Ansi.Color.GREEN, Ansi.Color.MAGENTA});
        THEMES.put(Role.DIRECTIVE, new Ansi.Color[]{Ansi.Color.CYAN, Ansi.Color.MAGENTA});
        THEMES.put(Role.ATTRIBUTE, new Ansi.Color[]{Ansi.Color.BLUE, Ansi.Color.CYAN});
        THEMES.put(Role.CONTEXT, new Ansi.Color[]{Ansi.Color.GREEN, Ansi.Color.YELLOW});
        THEMES.put(Role.DETAIL, new Ansi.Color[]{Ansi.Color.CYAN, Ansi.Color.BLUE});
        THEMES.put(Role.DEPENDENCY, new Ansi.Color[]{Ansi.Color.MAGENTA, Ansi.Color.CYAN});
        THEMES.put(Role.ERROR, new Ansi.Color[]{Ansi.Color.RED, Ansi.Color.MAGENTA});

        String theme = System.getProperty("theme", "dark").toLowerCase();
        ThemeManager.setTheme(theme);
        AnsiConsole.systemInstall();
    }

    public static void setTheme(String theme) {
        isDarkMode = !"light".equalsIgnoreCase(theme);
    }

    public static ColorBuilder builder() {
        return new ColorBuilder();
    }

    private static Ansi color(Role role) {
        Ansi.Color[] colors = THEMES.getOrDefault(role, new Ansi.Color[]{Ansi.Color.DEFAULT, Ansi.Color.DEFAULT});
        Ansi.Color selectedColor = isDarkMode ? colors[0] : colors[1];
        return isDarkMode ? Ansi.ansi().fgBright(selectedColor) : Ansi.ansi().fg(selectedColor);
    }

    public static class ColorBuilder {
        private final StringBuilder builder = new StringBuilder();

        public ColorBuilder add(Role role, String text) {
            builder.append(color(role).a(text).reset().toString());
            return this;
        }

        public String build() {
            return builder.toString();
        }
    }
}
