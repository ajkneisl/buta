package dev.shog.buta.commands.permission

import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

/**
 * A permission handle, from [PermissionFactory].
 */
abstract class Permable {
    /**
     * If a [member] has permission. This is in a guild environment.
     */
    abstract fun hasPermission(member: Member): Mono<Boolean>

    /**
     * If a [user] has permission. This is in a PM environment.
     */
    abstract fun hasPermission(user: User): Mono<Boolean>

    /**
     * Checks a [e] has permission.
     */
    fun check(e: MessageCreateEvent): Mono<Boolean> {
        val user = when {
            e.member.isPresent -> hasPermission(e.member.get())
            e.message.author.isPresent -> hasPermission(e.message.author.get())
            else -> Mono.empty()
        }

        return user
                .switchIfEmpty(Mono.just(false))
    }
}