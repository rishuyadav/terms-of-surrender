package dev.termsofsurrender.documents;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Absolute paths are required because each worker runs in a private temporary directory. */
final class ParserCommand {
    private ParserCommand() {}

    static List<String> create(Path directory, String main, String... arguments) {
        String classpath =
                Arrays.stream(
                                System.getProperty(
                                                "surefire.test.class.path",
                                                System.getProperty("java.class.path"))
                                        .split(File.pathSeparator))
                        .map(entry -> Path.of(entry).toAbsolutePath().normalize().toString())
                        .collect(Collectors.joining(File.pathSeparator));
        List<String> command =
                new ArrayList<>(
                        List.of(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-Xmx128m",
                                "-Xss512k",
                                "-XX:MaxMetaspaceSize=96m",
                                "-XX:ReservedCodeCacheSize=32m",
                                "-XX:MaxDirectMemorySize=16m",
                                "-XX:ActiveProcessorCount=1",
                                "-XX:+UseSerialGC",
                                "-XX:+ExitOnOutOfMemoryError",
                                "-XX:-CreateCoredumpOnCrash",
                                "-XX:-UsePerfData",
                                "-XX:ErrorFile="
                                        + (File.separatorChar == '/' ? "/dev/null" : "NUL"),
                                "-Djava.awt.headless=true",
                                "-Djava.io.tmpdir=" + directory,
                                "-Duser.home=" + directory));
        try {
            Class.forName(
                    "org.springframework.boot.loader.launch.PropertiesLauncher",
                    false,
                    ParserCommand.class.getClassLoader());
            command.add("-Dloader.main=" + main);
            command.add("-cp");
            command.add(classpath);
            command.add("org.springframework.boot.loader.launch.PropertiesLauncher");
            command.addAll(Arrays.asList(arguments));
        } catch (ClassNotFoundException exception) {
            command.add("-cp");
            command.add(classpath);
            command.add(main);
            command.addAll(Arrays.asList(arguments));
        }
        return command;
    }
}
