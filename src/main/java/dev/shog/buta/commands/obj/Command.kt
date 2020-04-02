package dev.shog.buta.commands.obj

import dev.shog.buta.commands.api.factory.GuildFactory
import dev.shog.buta.commands.obj.msg.MessageHandler
import dev.shog.buta.util.update
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

/**
 * A command.
 *
 * @param cfg The config
 */
abstract class Command(val cfg: CommandConfig) : ICommand {
    val container = MessageHandler.MessageContainer(cfg.name)

    override fun help(e: MessageCreateEvent): Mono<*> =
            e.message.guild
                    .map { g -> g.id.asLong() }
                    .flatMap { id -> GuildFactory.getObject(id) }
                    .zipWith(e.message.channel)
                    .flatMap { zip ->
                        val ch = zip.t2
                        val g = zip.t1

                        ch.createEmbed { embed ->
                            embed.update(e.message.author.get())

                            embed.setTitle(cfg.name)
                            embed.setDescription(cfg.desc)

                            MessageHandler.data
                                    .getJSONObject(cfg.name)
                                    .getJSONObject("help")
                                    .toMap()
                                    .forEach { pair ->
                                        embed.addField("${g.prefix}${pair.key}", pair.value.toString(), false)
                                    }
                        }
                    }
                    .then()
}