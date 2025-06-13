package su.terrafirmagreg.core.common.integration;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Модификатор рецептов для правильной обработки системы порчи еды TerraFirmaCraft.
 * Копирует данные о еде (дату создания, черты, состояние порчи) из входных предметов в выходные.
 * ИЗМЕНЕНО: Теперь всегда копирует данные о еде, включая из реальных предметов в слотах машины.
 */
public class TFCFoodModifier implements RecipeModifier {

    public static final TFCFoodModifier INSTANCE = new TFCFoodModifier();

    @Override
    public @NotNull ModifierFunction getModifier(@NotNull MetaMachine machine, @NotNull GTRecipe recipe) {
        return original -> {
            // Проверяем, есть ли в рецепте еда TFC
            if (!hasTFCFood(original)) {
                return original;
            }

            su.terrafirmagreg.core.TFGCore.LOGGER.debug("Применяем модификатор TFC для рецепта {} в машине {}", 
                original.id, machine.getDefinition().getId());

            GTRecipe modified = original.copy();

            // Копируем данные о еде из входов в выходы
            copyFoodDataFromInputsToOutputs(modified, machine);

            return modified;
        };
    }

    /**
     * Проверяет, содержит ли рецепт еду TFC
     */
    private boolean hasTFCFood(GTRecipe recipe) {
        List<Content> inputs = recipe.getInputContents(ItemRecipeCapability.CAP);
        List<Content> outputs = recipe.getOutputContents(ItemRecipeCapability.CAP);

        // Проверяем входы
        for (Content content : inputs) {
            if (hasContentTFCFood(content)) {
                return true;
            }
        }

        // Проверяем выходы
        for (Content content : outputs) {
            if (hasContentTFCFood(content)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Проверяет, содержит ли Content еду TFC
     */
    private boolean hasContentTFCFood(Content content) {
        Object contentObj = content.content;
        
        if (contentObj instanceof ItemStack stack) {
            return FoodCapability.get(stack) != null;
        } else if (contentObj instanceof Ingredient ingredient) {
            ItemStack[] items = ingredient.getItems();
            for (ItemStack item : items) {
                if (FoodCapability.get(item) != null) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Копирует данные о еде из входных предметов в выходные
     * ИЗМЕНЕНО: Теперь приоритетно использует данные из реальных предметов в слотах машины
     */
    private void copyFoodDataFromInputsToOutputs(GTRecipe recipe, MetaMachine machine) {
        List<Content> inputs = recipe.getInputContents(ItemRecipeCapability.CAP);
        List<Content> outputs = recipe.getOutputContents(ItemRecipeCapability.CAP);

        // Сначала пытаемся найти данные о еде в реальных предметах машины
        IFood sourceFood = getFoodFromMachineSlots(machine);
        
        // Если не нашли в машине, ищем в рецепте
        if (sourceFood == null) {
            for (Content inputContent : inputs) {
                IFood food = getContentFood(inputContent);
                if (food != null) {
                    sourceFood = food;
                    su.terrafirmagreg.core.TFGCore.LOGGER.debug("Используем данные о еде из рецепта");
                    break;
                }
            }
        } else {
            su.terrafirmagreg.core.TFGCore.LOGGER.debug("Используем данные о еде из реальных предметов в машине");
        }

        if (sourceFood == null) {
            su.terrafirmagreg.core.TFGCore.LOGGER.debug("Не найдены данные о еде для копирования");
            return;
        }

        // Логируем состояние исходной еды
        su.terrafirmagreg.core.TFGCore.LOGGER.info("Копируем данные о еде: дата создания = {}, гнилая = {}", 
            sourceFood.getCreationDate(), sourceFood.isRotten());

        // Копируем данные о еде во все выходные предметы, которые являются едой
        for (Content outputContent : outputs) {
            applyFoodDataToContent(outputContent, sourceFood);
        }
    }

    /**
     * Получает данные о еде из реальных предметов в слотах машины
     */
    public static IFood getFoodFromMachineSlots(MetaMachine machine) {
        try {
            // Получаем все трейты машины
            for (var trait : machine.getTraits()) {
                // Ищем трейты, которые являются обработчиками предметов
                if (trait instanceof com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler itemHandler) {
                    // Проверяем только входные слоты
                    if (itemHandler.getHandlerIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.IN || 
                        itemHandler.getHandlerIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.BOTH) {
                        
                        // Проверяем каждый слот
                        for (int i = 0; i < itemHandler.getSlots(); i++) {
                            ItemStack stack = itemHandler.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                IFood food = FoodCapability.get(stack);
                                if (food != null) {
                                    su.terrafirmagreg.core.TFGCore.LOGGER.debug("Найдена еда в слоте {}: {} (гнилая: {})", 
                                        i, stack.getDisplayName().getString(), food.isRotten());
                                    return food; // Возвращаем первую найденную еду
                                }
                            }
                        }
                    }
                }
                // Также проверяем прокси-трейты
                else if (trait instanceof com.gregtechceu.gtceu.api.machine.trait.ItemHandlerProxyTrait proxyTrait) {
                    if (proxyTrait.getCapabilityIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.IN || 
                        proxyTrait.getCapabilityIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.BOTH) {
                        
                        if (proxyTrait.getProxy() != null) {
                            for (int i = 0; i < proxyTrait.getSlots(); i++) {
                                ItemStack stack = proxyTrait.getStackInSlot(i);
                                if (!stack.isEmpty()) {
                                    IFood food = FoodCapability.get(stack);
                                    if (food != null) {
                                        su.terrafirmagreg.core.TFGCore.LOGGER.debug("Найдена еда в прокси-слоте {}: {} (гнилая: {})", 
                                            i, stack.getDisplayName().getString(), food.isRotten());
                                        return food; // Возвращаем первую найденную еду
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Если это мультиблочная машина-контроллер, проверяем её части
            if (machine instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController multiController) {
                for (var part : multiController.getParts()) {
                    if (part instanceof MetaMachine partMachine) {
                        IFood food = getFoodFromMachineSlots(partMachine);
                        if (food != null) {
                            return food;
                        }
                    }
                }
            }
        } catch (Exception e) {
            su.terrafirmagreg.core.TFGCore.LOGGER.error("Ошибка при получении данных о еде из слотов машины: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Получает IFood из Content
     */
    private IFood getContentFood(Content content) {
        Object contentObj = content.content;
        
        if (contentObj instanceof ItemStack stack) {
            return FoodCapability.get(stack);
        } else if (contentObj instanceof Ingredient ingredient) {
            ItemStack[] items = ingredient.getItems();
            for (ItemStack item : items) {
                IFood food = FoodCapability.get(item);
                if (food != null) {
                    return food;
                }
            }
        }
        
        return null;
    }

    /**
     * Применяет данные о еде к Content
     */
    private void applyFoodDataToContent(Content content, IFood sourceFood) {
        Object contentObj = content.content;
        
        if (contentObj instanceof ItemStack stack) {
            IFood outputFood = FoodCapability.get(stack);
            if (outputFood != null) {
                copyFoodData(sourceFood, outputFood);
                su.terrafirmagreg.core.TFGCore.LOGGER.debug("Скопированы данные о еде в выходной предмет: {}", 
                    stack.getDisplayName().getString());
            }
        } else if (contentObj instanceof Ingredient ingredient) {
            ItemStack[] items = ingredient.getItems();
            for (ItemStack item : items) {
                IFood outputFood = FoodCapability.get(item);
                if (outputFood != null) {
                    copyFoodData(sourceFood, outputFood);
                    su.terrafirmagreg.core.TFGCore.LOGGER.debug("Скопированы данные о еде в выходной ингредиент: {}", 
                        item.getDisplayName().getString());
                }
            }
        }
    }

    /**
     * Копирует данные о еде из источника в цель
     */
    private void copyFoodData(IFood source, IFood target) {
        // Копируем дату создания (определяет порчу)
        target.setCreationDate(source.getCreationDate());
        
        // Копируем черты еды
        target.getTraits().clear();
        target.getTraits().addAll(source.getTraits());
        
        su.terrafirmagreg.core.TFGCore.LOGGER.debug("Скопированы данные о еде: дата создания = {}, черт = {}", 
            source.getCreationDate(), source.getTraits().size());
    }

    /**
     * Статический метод для упрощения применения модификатора
     */
    public static GTRecipe apply(GTRecipe recipe, MetaMachine machine) {
        if (recipe == null) {
            return null;
        }
        
        try {
            TFCFoodModifier modifier = new TFCFoodModifier();
            return modifier.getModifier(machine, recipe).apply(recipe);
        } catch (Exception e) {
            su.terrafirmagreg.core.TFGCore.LOGGER.error("Ошибка при применении модификатора TFC: {}", e.getMessage());
            return recipe;
        }
    }
}