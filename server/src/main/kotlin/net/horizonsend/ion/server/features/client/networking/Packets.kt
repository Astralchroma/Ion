package net.horizonsend.ion.server.features.client.networking

import net.horizonsend.ion.server.features.client.networking.packets.GetPosPacket
import net.horizonsend.ion.server.features.client.networking.packets.HandshakePacket
import net.horizonsend.ion.server.features.client.networking.packets.PlayerAdd
import net.horizonsend.ion.server.features.client.networking.packets.PlayerRemove
import net.horizonsend.ion.server.features.client.networking.packets.ShipData
import net.horizonsend.ion.server.features.client.networking.packets.WorldPacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import org.bukkit.entity.Player

abstract class IonPacketHandler {
	abstract val name: String
	val id by lazy { id(name.lowercase()) }

	open fun s2c(
		buf: FriendlyByteBuf,
		player: Player,
		vararg arguments: Any
	) {
	}

	open fun c2s(
		buf: FriendlyByteBuf,
		player: Player
	) {
	}

	fun id(s: String) = ResourceLocation.fromNamespaceAndPath("ion", s)
}

enum class Packets(
	val handler: IonPacketHandler
) {
	HANDSHAKE(HandshakePacket),
	GETPOS(GetPosPacket),
	SHIP_DATA(ShipData),
	WORLD_PACKET(WorldPacket),
	PLAYER_ADD(PlayerAdd),
	PLAYER_REMOVE(PlayerRemove);
	val id get() = handler.id

	fun send(player: Player, vararg args: Any) {
//		(player as CraftPlayer).handle.connection.send( TODO
//			ClientboundCustomPayloadPacket(
//				id,
//				FriendlyByteBuf(Unpooled.buffer()).apply { handler.s2c(this, player, *args) })
//		)
	}

	fun broadcast(vararg args: Any) {
//		for (uuid in VoidNetwork.modUsers) TODO
//			(Bukkit.getPlayer(uuid) as CraftPlayer).handle.connection.send(
//				ClientboundCustomPayloadPacket(
//					id,
//					FriendlyByteBuf(Unpooled.buffer()).apply { handler.s2c(this, Bukkit.getPlayer(uuid)!!, args) })
//			)
	}
}
