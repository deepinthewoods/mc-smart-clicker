package ninja.trek.smartclicker.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import ninja.trek.smartclicker.SmartClickerClient;
import ninja.trek.smartclicker.recording.RecordingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class DropItemMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    private void onHandleInventoryMouseClick(int containerId, int slotId, int button, ContainerInput clickType, Player player, CallbackInfo ci) {
        RecordingManager recordingManager = SmartClickerClient.getRecordingManager();
        if (recordingManager == null || !recordingManager.isRecording()) return;

        // Click outside inventory window — dropping whatever is on the cursor
        if (slotId == -999 && clickType == ContainerInput.PICKUP) {
            recordingManager.onInventoryThrow(true);
            return;
        }

        // Q/Ctrl+Q on a hovered slot
        if (clickType == ContainerInput.THROW) {
            boolean entireStack = button == 1;
            recordingManager.onInventoryThrow(entireStack);
        }
    }
}
