package com.bazaarflipper.discord;

import com.bazaarflipper.util.Logger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * JDA 5.x via jar-in-jar.
 * Bot commands: !status, !stop, !pause, !resume, !budget, !flips, !profit, !mayor, !reconnect, !break, !tax
 * Thread safety: JDA commands on JDA threads. ConcurrentLinkedQueue inbox polled by game thread via mc.execute()
 */
public class DiscordBotClient extends ListenerAdapter {

    private final String token;
    private final String commandChannelId;
    private JDA jda;
    private final ConcurrentLinkedQueue<String> commandInbox = new ConcurrentLinkedQueue<>();
    private volatile boolean running = false;

    // Reference to mod for callbacks - set later
    private Runnable stopCallback;
    private Runnable pauseCallback;
    private Runnable resumeCallback;
    private Runnable reconnectCallback;

    public DiscordBotClient(String token, String channelId) {
        this.token = token;
        this.commandChannelId = channelId;
    }

    public void setCallbacks(Runnable stop, Runnable pause, Runnable resume, Runnable reconnect) {
        this.stopCallback = stop;
        this.pauseCallback = pause;
        this.resumeCallback = resume;
        this.reconnectCallback = reconnect;
    }

    public void start() {
        if (running) return;
        if (token == null || token.isBlank()) {
            Logger.warn("Discord bot token empty, not starting");
            return;
        }
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.DIRECT_MESSAGES)
                    .addEventListeners(this)
                    .build();
            jda.awaitReady();
            running = true;
            Logger.info("Discord bot started");
        } catch (Exception e) {
            Logger.error("Failed to start Discord bot", e);
        }
    }

    public void stop() {
        running = false;
        if (jda != null) {
            jda.shutdown();
            jda = null;
        }
    }

    public void sendMessage(String message) {
        if (jda == null || commandChannelId == null || commandChannelId.isBlank()) return;
        try {
            TextChannel channel = jda.getTextChannelById(commandChannelId);
            if (channel != null) {
                channel.sendMessage(message).queue();
            }
        } catch (Exception e) {
            Logger.error("Bot send message failed", e);
        }
    }

    public void sendEmbed(String jsonPayload) {
        // For bot, we can send plain text or embed; simplify to text
        sendMessage(jsonPayload);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (commandChannelId != null && !commandChannelId.isBlank()) {
            if (!event.getChannel().getId().equals(commandChannelId)) return;
        }
        String content = event.getMessage().getContentRaw().trim();
        if (!content.startsWith("!")) return;

        String cmd = content.split(" ")[0].toLowerCase();
        commandInbox.offer(cmd);

        // Handle immediately on JDA thread but post to game thread via callbacks that use mc.execute
        switch (cmd) {
            case "!stop" -> {
                if (stopCallback != null) stopCallback.run();
                event.getChannel().sendMessage("Stopping flip engine...").queue();
            }
            case "!pause" -> {
                if (pauseCallback != null) pauseCallback.run();
                event.getChannel().sendMessage("Pausing flipping...").queue();
            }
            case "!resume" -> {
                if (resumeCallback != null) resumeCallback.run();
                event.getChannel().sendMessage("Resuming flipping...").queue();
            }
            case "!reconnect" -> {
                if (reconnectCallback != null) reconnectCallback.run();
                event.getChannel().sendMessage("Forcing reconnect...").queue();
            }
            case "!status", "!budget", "!flips", "!profit", "!mayor", "!break", "!tax" -> {
                // These are polled by game thread - for now respond placeholder
                event.getChannel().sendMessage("Command " + cmd + " received, processing on game thread...").queue();
            }
        }
    }

    public ConcurrentLinkedQueue<String> getCommandInbox() { return commandInbox; }

    public boolean isRunning() { return running; }
}
