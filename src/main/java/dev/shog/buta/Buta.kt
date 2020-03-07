package dev.shog.buta

import dev.shog.buta.commands.api.factory.GuildFactory
import dev.shog.buta.commands.commands.*
import dev.shog.buta.commands.obj.ICommand
import dev.shog.buta.events.GuildJoinEvent
import dev.shog.buta.events.GuildLeaveEvent
import dev.shog.buta.events.PresenceHandler
import dev.shog.buta.handle.ButaConfig
import dev.shog.buta.handle.msg.MessageHandler
import dev.shog.buta.handle.StatisticsManager
import dev.shog.buta.handle.audio.AudioManager
import dev.shog.lib.app.AppBuilder
import dev.shog.lib.cfg.ConfigHandler
import dev.shog.lib.hook.DiscordWebhook
import discord4j.core.DiscordClient
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.guild.GuildCreateEvent
import discord4j.core.event.domain.guild.GuildDeleteEvent
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import reactor.core.publisher.Hooks
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask
import kotlin.system.exitProcess

/** Developers */
val DEV = arrayOf(274712215024697345L)

/**
 * The main LOGGER
 */
val LOGGER = LoggerFactory.getLogger("Buta Instance")!!

/**
 * App
 */
val APP = AppBuilder()
        .usingConfig(ConfigHandler.createConfig(ConfigHandler.ConfigType.YML, "buta", ButaConfig()))
        .withCache()
        .withName("buta")
        .withVersion(1.2F)
        .withWebhook { DiscordWebhook(this!!.asObject<ButaConfig>().webhook ?: "") }
        .build()

/**
 * The main Discord Client.
 */
var CLIENT: GatewayDiscordClient? = null

/**
 * If Buta is running in production mode.
 */
var PRODUCTION: Boolean = false

fun main(args: Array<String>) = runBlocking<Unit> {
    val key = APP.getConfigObject<ButaConfig>().token

    if (key == null) {
        LOGGER.error("Please fill out the configuration file!")
        exitProcess(-1)
    }

    when {
        args.contains("--prod") -> {
            LOGGER.info("Starting Buta in Production mode")

            PRODUCTION = true
        }

        !args.contains("--prod") -> {
            LOGGER.info("Starting Buta in Non-Production mode")

            PRODUCTION = false

            Hooks.onOperatorDebug() // this adds extra debug onto reactor stuff, super cool
        }
    }

    initCommands()

    DiscordClient
            .create(key)
            .login()
            .doOnNext { cli -> CLIENT = cli }
            .block()

    CLIENT?.apply {
        on(GuildCreateEvent::class.java)
                .flatMap { GuildJoinEvent.invoke(it) }
                .subscribe()

        on(GuildDeleteEvent::class.java)
                .flatMap { GuildLeaveEvent.invoke(it) }
                .subscribe()

        on(MessageCreateEvent::class.java)
                .flatMap { dev.shog.buta.events.MessageEvent.invoke(it) }
                .subscribe()

        on(ReactionAddEvent::class.java)
                .filter { event -> Uno.wildWaiting.containsKey(event.userId) && Uno.properColors.contains(event.emoji) }
                .filter { event ->
                    val time = Uno.wildWaiting[event.userId]?.time ?: 0

                    // Make sure the request isn't 10 seconds old TODO purge if so
                    System.currentTimeMillis() - time < TimeUnit.SECONDS.toMillis(10)
                }
                .flatMap { ev -> Uno.completedWildCard(ev) }
                .subscribe()

        on(VoiceStateUpdateEvent::class.java)
                .filterWhen { event ->
                    event.client.selfId
                            .map { id -> id == event.current.userId }
                }
                .filter { event -> !event.current.channelId.isPresent }
                .map { event -> AudioManager.getGuildMusicManager(event.current.guildId) }
                .doOnNext { guild -> guild.stop(true) }
                .subscribe()

        on(MemberJoinEvent::class.java)
                .filterWhen { e ->
                    e.client.self
                            .flatMap { self -> self.asMember(e.guildId) }
                            .flatMap { member -> member.basePermissions }
                            .map { perms -> perms.contains(Permission.ADMINISTRATOR) }
                }
                .flatMap { e ->
                    GuildFactory.getObject(e.guildId.asLong())
                            .map { guild -> guild.joinRole }
                            .filter { duo -> duo.first == true && duo.second != null && duo.second != -1L }
                            .filterWhen { duo ->
                                e.client.self
                                        .flatMap { self -> self.asMember(e.guildId) }
                                        .zipWith(e.guild.flatMap { guild -> guild.getRoleById(Snowflake.of(duo.second!!)) })
                                        .flatMap { zip -> zip.t1.hasHigherRoles(setOf(zip.t2.id)) }
                            }
                            .flatMap { duo -> e.member.addRole(Snowflake.of(duo.second!!)) }
                }
                .subscribe()

        on(ReadyEvent::class.java)
                .flatMap { PresenceHandler.invoke(it) }
                .subscribe()
    }

    Runtime.getRuntime().addShutdownHook(Thread(StatisticsManager::save))

    Timer().schedule(timerTask {
        StatisticsManager.save()
    }, 0, 1000 * 60 * 60) // Hourly

    CLIENT?.onDisconnect()?.block()
}

/**
 * Initialize commands
 */
private fun initCommands() {
    initInfo()
    initAudio()
    initFun()
    initAdmin()
    initDev()
    initGambling()
    ICommand.COMMANDS.add(Uno)
}