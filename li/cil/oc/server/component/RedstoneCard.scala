package li.cil.oc.server.component

import li.cil.oc.api.network.{Component, Visibility, Message}
import net.minecraftforge.common.ForgeDirection

class RedstoneCard extends Component {
  override val name = "redstone"

  override val visibility = Visibility.Neighbors

  componentVisibility = visibility

  override def receive(message: Message) = super.receive(message).orElse {
    message.data match {
      case Array(target: Array[Byte], side: Double) if message.name == "redstone.input" =>
        network.get.sendToAddress(this, new String(target, "UTF-8"),
          "redstone.input", ForgeDirection.getOrientation(side.toInt))
      case Array(target: Array[Byte], side: Double) if message.name == "redstone.output" =>
        network.get.sendToAddress(this, new String(target, "UTF-8"),
          "redstone.output", ForgeDirection.getOrientation(side.toInt))
      case Array(target: Array[Byte], side: Double, value: Double) if message.name == "redstone.output=" =>
        network.get.sendToAddress(this, new String(target, "UTF-8"),
          "redstone.output=", ForgeDirection.getOrientation(side.toInt), value.toInt)
      case _ => None // Ignore.
    }
  }
}
