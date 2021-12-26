package org.stonlexx.servercontrol.connection;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.stonlexx.servercontrol.api.MinecraftServerControlApi;
import org.stonlexx.servercontrol.api.ServiceManager;
import org.stonlexx.servercontrol.api.chat.ChatColor;
import org.stonlexx.servercontrol.api.command.CommandManager;
import org.stonlexx.servercontrol.api.scheduler.TaskScheduler;
import org.stonlexx.servercontrol.api.server.MinecraftServerType;
import org.stonlexx.servercontrol.api.server.type.ConnectedMinecraftServer;
import org.stonlexx.servercontrol.api.utility.FileUtil;
import org.stonlexx.servercontrol.api.utility.NumberUtil;
import org.stonlexx.servercontrol.connection.command.impl.HelpCommand;
import org.stonlexx.servercontrol.connection.command.impl.ServerCommand;
import org.stonlexx.servercontrol.connection.command.impl.ShutdownCommand;
import org.stonlexx.servercontrol.connection.command.impl.StatisticCommand;
import org.stonlexx.servercontrol.connection.server.execution.type.LinuxExecution;
import org.stonlexx.servercontrol.connection.server.execution.type.WindowsExecution;
import org.stonlexx.servercontrol.connection.server.type.SimpleConnectedMinecraftServer;
import org.stonlexx.servercontrol.connection.server.type.SimpleTemplateMinecraftServer;
import org.stonlexx.servercontrol.api.server.OSExecutionServer;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Log4j2
public final class SimpleMinecraftServerControlConnection
        implements MinecraftServerControlConnection {

    private final ServiceManager serviceManager;

    @Getter
    private final long runningMillis;


    @Override
    public void processHandle(@NonNull String logMessage,
                              @NonNull Runnable process) {

        long startTime = System.currentTimeMillis();

        log.info(String.format(" > %s...", logMessage));

        process.run();
        log.info(ChatColor.GREEN + String.format(" > Success: %sms", NumberUtil.spaced((int) (System.currentTimeMillis() - startTime))));
    }

    @Override
    public void resetRunningServers() {
        for (ConnectedMinecraftServer connectedMinecraftServer : serviceManager.getServerManager().getMinecraftServers()) {
            OSExecutionServer executionServer = getServerExecution(connectedMinecraftServer);

            if (connectedMinecraftServer.isRunning()) {
                executionServer.shutdownServer();

                connectedMinecraftServer.onShutdown();
            }
        }

        initMinecraftServers();
    }

    @Override
    public void initMinecraftCommands() {
        CommandManager commandManager = serviceManager.getCommandManager();

        commandManager.registerCommand(new ServerCommand());
        commandManager.registerCommand(new HelpCommand());
        commandManager.registerCommand(new ShutdownCommand());
        commandManager.registerCommand(new StatisticCommand());
    }

    @SneakyThrows
    @Override
    public void createDirectories() {

        if (!Files.exists(serviceManager.getTemplatesDirectory())) {
            Files.createDirectory(serviceManager.getTemplatesDirectory());
        }

        if (!Files.exists(serviceManager.getModulesDirectory())) {
            Files.createDirectory(serviceManager.getModulesDirectory());
        }

        if (!Files.exists(serviceManager.getLoggingDirectory())) {
            Files.createDirectory(serviceManager.getLoggingDirectory());
        }

        if (!Files.exists(serviceManager.getRunningDirectory())) {
            Files.createDirectory(serviceManager.getRunningDirectory());
        }

        if (!Files.exists(serviceManager.getGlobalFilesDirectory())) {
            Files.createDirectory(serviceManager.getGlobalFilesDirectory());
        }
    }

    @SneakyThrows
    @Override
    public void initMinecraftServers() {
        serviceManager.getServerManager().getMinecraftServers().clear();
        serviceManager.getServerManager().getTemplateServers().clear();

        File[] shapeServerDirectories = serviceManager.getTemplatesDirectory().toFile().listFiles();

        if (shapeServerDirectories == null) {
            return;
        }

        // Get shape minecraft servers
        for (File shapeServerDirectory : shapeServerDirectories) {

            // Server property
            Properties shapeProperties  = new Properties();
            File shapePropertyFile      = shapeServerDirectory.toPath().resolve("template.properties").toFile();

            if (!shapePropertyFile.exists()) {
                continue;
            }

            FileUtil.input(shapePropertyFile, shapeProperties::load);

            SimpleTemplateMinecraftServer simpleTemplateMinecraftServer = new SimpleTemplateMinecraftServer(

                    shapeServerDirectory.getName(), shapeServerDirectory.toPath(), shapeProperties,
                    MinecraftServerType.getTypeByLevel(Integer.parseInt(shapeProperties.getProperty("type")))
            );

            // Get sub minecraft servers
            File[] subServerDirectories = shapeServerDirectory.listFiles();

            if (subServerDirectories == null) {
                continue;
            }

            for (File subServerDirectory : subServerDirectories) {

                // Server property
                Properties subProperties    = new Properties();
                File subPropertyFile        = subServerDirectory.toPath().resolve("mccontrol.properties").toFile();

                if (!subPropertyFile.exists()) {
                    continue;
                }

                FileUtil.input(subPropertyFile, subProperties::load);

                String subServerName = subProperties.getProperty("server.name");
                String version = subProperties.getProperty("server.version", "1.12.2");

                // Create sub server
                SimpleConnectedMinecraftServer simpleConnectedMinecraftServer = new SimpleConnectedMinecraftServer(
                        subServerName, version,

                        simpleTemplateMinecraftServer,
                        subProperties,

                        subServerDirectory.toPath()
                );

                simpleTemplateMinecraftServer.addConnectedServer(simpleConnectedMinecraftServer);
                serviceManager.getServerManager().addConnectedServer(simpleConnectedMinecraftServer);
            }

            serviceManager.getServerManager().addTemplateServer(simpleTemplateMinecraftServer);
        }
    }

    @Override
    public void initTicker(long millis) {
        new TaskScheduler() {

            @Override
            public void run() {

                // check current running servers.
                for (ConnectedMinecraftServer connectedMinecraftServer : serviceManager.getServerManager().getMinecraftServers()) {

                    // todo: connect to the server channel
                }
            }

        }.runTimer(millis, millis, TimeUnit.MILLISECONDS);
    }


    Map<String, OSExecutionServer> SERVER_EXECUTION_MAP = new ConcurrentHashMap<>();

    @Override
    public OSExecutionServer getServerExecution(@NonNull ConnectedMinecraftServer server) {
        return SERVER_EXECUTION_MAP.computeIfAbsent(server.getName().toLowerCase(), f -> MinecraftServerControlApi.getInstance().isWindows()
                ? new WindowsExecution(server) : new LinuxExecution(server));
    }
}
