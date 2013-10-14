package li.cil.oc.common.tileentity

import java.util.concurrent.atomic.AtomicBoolean
import li.cil.oc.api.driver.Slot
import li.cil.oc.api.network.Receiver
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.server.component
import li.cil.oc.server.component.Redstone
import li.cil.oc.server.driver
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.ForgeDirection

class Computer(isClient: Boolean) extends Rotatable with component.Computer.Environment with ComponentInventory with Redstone with Receiver {
  def this() = this(false)

  // ----------------------------------------------------------------------- //

  private val hasChanged = new AtomicBoolean(true) // For `markChanged`.

  private var isRunning = false

  // ----------------------------------------------------------------------- //

  override protected val computer = if (isClient) null else new component.Computer(this)

  def world = worldObj

  def markAsChanged() = hasChanged.set(true)

  // ----------------------------------------------------------------------- //

  def turnOn() = computer.start()

  def turnOff() = computer.stop()

  def isOn = isRunning

  def isOn_=(value: Boolean) = {
    isRunning = value
    worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord)
    this
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBT(nbt: NBTTagCompound) = {
    super.readFromNBT(nbt)
    load(nbt.getCompoundTag("node"))
    computer.recomputeMemory()
  }

  override def writeToNBT(nbt: NBTTagCompound) = {
    super.writeToNBT(nbt)

    val nodeNbt = new NBTTagCompound
    save(nodeNbt)
    nbt.setCompoundTag("node", nodeNbt)
  }

  // ----------------------------------------------------------------------- //

  override def updateEntity() = if (!worldObj.isRemote) {
    computer.update()
    update()
    if (hasChanged.get)
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
    if (isRunning != computer.isRunning)
      ServerPacketSender.sendComputerState(this, computer.isRunning)
    isRunning = computer.isRunning

    for (component <- components) component match {
      case Some(node) => node.update()
      case _ => // Empty.
    }
  }

  override def validate() = {
    super.validate()
    if (worldObj.isRemote) {
      ClientPacketSender.sendComputerStateRequest(this)
      ClientPacketSender.sendRedstoneStateRequest(this)
    }
  }

  // ----------------------------------------------------------------------- //

  def getInvName = "oc.container.computer"

  def getSizeInventory = 8

  def isItemValidForSlot(slot: Int, item: ItemStack) = (slot, Registry.driverFor(item)) match {
    case (_, None) => false // Invalid item.
    case (0, Some(driver)) => driver.slot(item) == Slot.Power
    case (1 | 2 | 3, Some(driver)) => driver.slot(item) == Slot.Card
    case (4 | 5, Some(driver)) => driver.slot(item) == Slot.Memory
    case (6 | 7, Some(driver)) => driver.slot(item) == Slot.HardDiskDrive
    case _ => false // Invalid slot.
  }

  override def isUseableByPlayer(player: EntityPlayer) =
    worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this &&
      player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) < 64

  override def onInventoryChanged() {
    super.onInventoryChanged()
    if (!worldObj.isRemote) {
      computer.recomputeMemory()
      isOutputEnabled = hasRedstoneCard
    }
  }

  // ----------------------------------------------------------------------- //

  def canConnectRedstone(side: ForgeDirection) = isOutputEnabled

  override def input(side: ForgeDirection) = {
    val global = toGlobal(side)
    worldObj.isBlockProvidingPowerTo(
      xCoord + global.offsetX, yCoord + global.offsetY, zCoord + global.offsetZ, global.getOpposite.ordinal)
  }

  override protected def onRedstoneOutputChanged(side: ForgeDirection) = {
    super.onRedstoneOutputChanged(side)
    if (side == ForgeDirection.UNKNOWN) {
      worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType.blockID)
    }
    else {
      val global = toGlobal(side)
      worldObj.notifyBlockOfNeighborChange(xCoord + global.offsetX, yCoord + global.offsetY, zCoord + global.offsetZ, getBlockType.blockID)
    }
    if (!worldObj.isRemote) ServerPacketSender.sendRedstoneState(this)
    else worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord)
  }

  private def hasRedstoneCard = inventory.exists {
    case Some(item) => driver.RedstoneCard.worksWith(item)
    case _ => false
  }
}