package net.aquiles.neochat.utils;

import net.aquiles.neochat.NeoChat;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;

public class UpdateChecker {

    private final NeoChat plugin;
    private final String url;

    public UpdateChecker(NeoChat plugin, String url) {
        this.plugin = plugin;
        this.url = url;
    }

    public void getVersion(final Consumer<String> consumer) {
        plugin.runAsync(() -> {
            try (InputStream inputStream = new URL(this.url).openStream();
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    consumer.accept(scanner.next());
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Unable to search for updates: " + exception.getMessage());
            }
        });
    }
}
