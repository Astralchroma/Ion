package net.horizonsend.ion.common.utils.redis

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.horizonsend.ion.common.CommonConfig
import net.horizonsend.ion.common.IonComponent
import net.horizonsend.ion.common.database.DBManager.jedisPool
import net.horizonsend.ion.common.database.Oid
import net.horizonsend.ion.common.utils.redis.gson.OidJsonSerializer
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPubSub
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory

object RedisActions : IonComponent() {
	private val gson = GsonBuilder()
		.registerTypeAdapter(Oid::class.java, OidJsonSerializer)
		.create()

	private val idActionMap = mutableMapOf<String, RedisPubSubAction<*>>()

	private lateinit var channel: String
	private lateinit var pubSub: JedisPubSub
	private lateinit var subscriber: Jedis

	var enabled = false

	override fun onEnable() {
		connect()

		Thread({ subscriber.subscribe(pubSub, channel) }, "Ion Plugin Messaging").start()

		enabled = true
	}

	private fun connect() {
		channel = CommonConfig.redis.channel

		pubSub = IonPubSubListener
		subscriber = jedisPool.resource

		log.info("Connected to Redis with channel $channel, pubSub: $pubSub, subscriber: $subscriber")
	}

	override fun onDisable() {
		enabled = false

		pubSub.unsubscribe() // stop listening for messages
		subscriber.close() // close the subscriber instance
		idActionMap.clear() // clear the plugin message map
	}

	// Actions
	fun <T : RedisPubSubAction<*>> register(message: T) {
		check(!idActionMap.containsKey(message.id)) { "Duplicate message ${message.id}" }
		idActionMap[message.id] = message
	}

	inline fun <reified T, B> register(id: String, runSync: Boolean, noinline function: (T) -> B): RedisPubSubAction<T> {
		val action = object : RedisPubSubAction<T>(id, object : TypeToken<T>() {}.type, runSync) {
			override fun onReceive(data: T) {
				function.invoke(data)
			}
		}
		register(action)
		return action
	}

	private val serverId = UUID.randomUUID()

	private val executor = Executors.newSingleThreadExecutor(object : ThreadFactory {
		private var counter: Int = 0

		override fun newThread(r: Runnable): Thread {
			return Thread(r, "redis-action-publisher-${counter++}")
		}
	})

	fun <Data> publish(messageId: String, data: Data, type: Type) : Future<Long?> {
		val content = gson.toJson(data, type)
		val message = "$serverId:$messageId:$content"

		@Suppress("UNCHECKED_CAST")
		val id: Future<Long?> = executor.submit {
			jedisPool.resource.use { it.publish(channel, message) }
		} as Future<Long?>

		return id
	}

	private object IonPubSubListener : JedisPubSub() {
		/** Upon receiving a message */
		override fun onMessage(channel: String, message: String) {
			// to prevent weird things from happening, delay all update handling till initialization is complete
			// however, we still need to listen immediately so we don't miss any updates
			if (!enabled) return

			val messageInfo = Message.breakMessage(message)

			if (messageInfo.serverId == serverId.toString()) return // ignore if it came from us
			val pluginMessage = idActionMap[messageInfo.actionId]

			if (pluginMessage == null) {
				log.warn("Unknown message ${messageInfo.actionId}. Full contents: $message")
				return
			}

			val content: String = messageInfo.jsonMessage

			val data = try {
				gson.fromJson<Any>(content, pluginMessage.type)
			} catch (exception: Exception) {
				log.error("Error while reading redis message $message", exception)
				return
			}

			try {
				pluginMessage.castAndReceive(data)
			} catch (exception: Exception) {
				log.error("Error while executing redis action $message", exception)
			}
		}
	}

	data class Message(
		val serverId: String,
		val actionId: String,
		val jsonMessage: String
	) {
		companion object {
			fun breakMessage(message: String): Message {
				val split = message.split(":")
				val serverId = split[0]

				val actionId = split[1]

				val content: String = message
					.removePrefix(split[0]).removePrefix(":")
					.removePrefix(split[1]).removePrefix(":")

				return Message(serverId, actionId, content)
			}
		}
	}
}