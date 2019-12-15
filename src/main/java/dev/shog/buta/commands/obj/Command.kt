package dev.shog.buta.commands.obj

import dev.shog.buta.commands.api.GuildFactory
import dev.shog.buta.commands.permission.Permable
import dev.shog.buta.util.update
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

/**
 * The main command interface.
 *
 * The command can be invoked by a user sending a message with the prefix then [commandName] in a channel where Buta can see it.
 *
 * If [isPmAvailable] is true, it can be accessible through pms.
 */
abstract class Command(
        val data: LangFillableContent,
        val isPmAvailable: Boolean,
        val category: Categories,
        val permable: Permable
) {
    /**
     * When the command is invoked by a user.
     */
    abstract fun invoke(e: MessageCreateEvent, args: MutableList<String>): Mono<Void>

    /**
     * When the command's help command is invoked by a user.
     */
    fun invokeHelp(e: MessageCreateEvent): Mono<Void> =
            e.message.guild
                    .map { g -> g.id.asLong() }
                    .flatMap { id -> GuildFactory.get(id) }
                    .zipWith(e.message.channel)
                    .flatMap { zip ->
                        val ch = zip.t2
                        val g = zip.t1

                        ch.createEmbed { embed ->
                            embed.update(e.message.author.get())

                            embed.setTitle(data.commandName)
                            embed.setDescription(data.commandDesc)

                            data.helpCommand.entries.forEach { pair ->
                                embed.addField("${g.prefix}${pair.key}", pair.value, false)
                            }
                        }
                    }
                    .then()

    /**
     * Add the [Command] to [COMMANDS]
     */
    fun add() {
        COMMANDS.add(this)
    }

    companion object {
        /**
         * All of the commands.
         */
        val COMMANDS = ArrayList<Command>()
    }
}