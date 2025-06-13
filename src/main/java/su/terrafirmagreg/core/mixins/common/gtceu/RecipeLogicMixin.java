package su.terrafirmagreg.core.mixins.common.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.terrafirmagreg.core.common.integration.TFCGTIntegration;

/**
 * Mixin для перехвата завершения рецептов в GregTech машинах
 * и применения данных о еде TFC к выходным предметам
 */
@Mixin(value = RecipeLogic.class, remap = false)
public class RecipeLogicMixin {

    @Shadow
    protected GTRecipe lastRecipe;

    @Shadow
    public IRecipeLogicMachine machine;

    /**
     * Сохраняем данные о еде ДО выполнения рецепта
     * Это позволяет захватить состояние входных предметов до их потребления
     */
    @Inject(method = "setupRecipe", at = @At("HEAD"))
    private void onSetupRecipeHead(GTRecipe recipe, CallbackInfo ci) {
        if (recipe != null && machine instanceof MetaMachine metaMachine) {
            // Сохраняем данные о еде из входных предметов ДО их потребления
            TFCGTIntegration.saveFoodDataBeforeRecipe(recipe, metaMachine);
        }
    }

    /**
     * Перехватываем момент завершения рецепта и применяем данные о еде к выходным предметам
     * Инъекция происходит в конце метода onRecipeFinish, после всех операций с выходными предметами
     */
    @Inject(method = "onRecipeFinish", at = @At("TAIL"))
    private void onRecipeFinishTail(CallbackInfo ci) {
        if (lastRecipe != null && machine instanceof MetaMachine metaMachine) {
            // Применяем сохраненные данные о еде к выходным предметам после их создания
            TFCGTIntegration.applyFoodDataToMachineOutputs(lastRecipe, metaMachine);
        }
    }
} 