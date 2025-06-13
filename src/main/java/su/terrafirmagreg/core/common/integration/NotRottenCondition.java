package su.terrafirmagreg.core.common.integration;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.mojang.serialization.Codec;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NotRottenCondition extends RecipeCondition {

    // TODO: Исправить создание RecipeConditionType
    // public static final RecipeConditionType<NotRottenCondition> TYPE =
    //         new RecipeConditionType<>();

    @Override
    public RecipeConditionType<?> getType() {
        return null;
    }

    @Override
    public boolean test(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        // Получаем входные предметы через ItemRecipeCapability
        List<Content> itemInputs = recipe.getInputContents(ItemRecipeCapability.CAP);

        return itemInputs.stream()
                .map(content -> (ItemStack) content.content)
                .allMatch(stack -> {
                    var food = FoodCapability.get(stack);
                    return food == null || !food.isRotten();
                });
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("tfc.tooltip.not_rotten");
    }

    @Override
    public RecipeCondition createTemplate() {
        return new NotRottenCondition();
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeBoolean(isReverse);
    }

    @Override
    public RecipeCondition fromNetwork(FriendlyByteBuf buffer) {
        return this.setReverse(buffer.readBoolean());
    }
}