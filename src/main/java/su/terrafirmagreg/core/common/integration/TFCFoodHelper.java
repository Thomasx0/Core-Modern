package su.terrafirmagreg.core.common.integration;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Утилитарный класс для работы с системой еды TerraFirmaCraft
 */
public class TFCFoodHelper {
    
    /**
     * Проверяет, содержит ли рецепт гнилую еду во входных ингредиентах
     * ИЗМЕНЕНО: Теперь используется только для логирования, рецепты не блокируются
     */
    public static boolean hasRottenFood(GTRecipe recipe) {
        var inputContents = recipe.getInputContents(ItemRecipeCapability.CAP);
        
        su.terrafirmagreg.core.TFGCore.LOGGER.debug("Проверяем рецепт {} на гнилую еду, входных ингредиентов: {}", 
            recipe.id, inputContents.size());
        
        for (var content : inputContents) {
            Object contentObj = content.getContent();
            
            if (contentObj instanceof ItemStack stack) {
                // Прямая проверка ItemStack
                IFood food = FoodCapability.get(stack);
                if (food != null && food.isRotten()) {
                    su.terrafirmagreg.core.TFGCore.LOGGER.info("НАЙДЕНА ГНИЛАЯ ЕДА в рецепте {}: {}", 
                        recipe.id, stack.getDisplayName().getString());
                    return true;
                }
            } else if (contentObj instanceof Ingredient ingredient) {
                // Проверка всех предметов в Ingredient
                ItemStack[] items = ingredient.getItems();
                su.terrafirmagreg.core.TFGCore.LOGGER.debug("Проверяем ингредиент с {} предметами", items.length);
                
                for (ItemStack item : items) {
                    IFood food = FoodCapability.get(item);
                    if (food != null) {
                        boolean isRotten = food.isRotten();
                        su.terrafirmagreg.core.TFGCore.LOGGER.debug("Найдена еда: {} (гнилая: {})", 
                            item.getDisplayName().getString(), isRotten);
                        
                        if (isRotten) {
                            su.terrafirmagreg.core.TFGCore.LOGGER.info("НАЙДЕНА ГНИЛАЯ ЕДА в рецепте {}: {}", 
                                recipe.id, item.getDisplayName().getString());
                            return true;
                        }
                    } else {
                        su.terrafirmagreg.core.TFGCore.LOGGER.debug("Предмет {} не является едой TFC", 
                            item.getDisplayName().getString());
                    }
                }
            } else {
                su.terrafirmagreg.core.TFGCore.LOGGER.debug("Неизвестный тип содержимого: {}", 
                    contentObj.getClass().getSimpleName());
            }
        }
        
        su.terrafirmagreg.core.TFGCore.LOGGER.debug("Гнилая еда в рецепте {} не найдена", recipe.id);
        return false;
    }
    
    /**
     * Проверяет, что вся еда в рецепте свежая (не гнилая)
     * ИЗМЕНЕНО: Теперь используется только для информационных целей
     */
    public static boolean allFoodFresh(GTRecipe recipe) {
        return !hasRottenFood(recipe);
    }
    
    /**
     * Получает первый предмет с данными о еде TFC из входных ингредиентов рецепта
     */
    @Nullable
    public static IFood getFirstFood(GTRecipe recipe) {
        var inputContents = recipe.getInputContents(ItemRecipeCapability.CAP);
        
        for (var content : inputContents) {
            Object contentObj = content.getContent();
            
            if (contentObj instanceof ItemStack stack) {
                IFood food = FoodCapability.get(stack);
                if (food != null) {
                    return food;
                }
            } else if (contentObj instanceof Ingredient ingredient) {
                ItemStack[] items = ingredient.getItems();
                for (ItemStack item : items) {
                    IFood food = FoodCapability.get(item);
                    if (food != null) {
                        return food;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Получает первый ItemStack с данными о еде TFC из входных ингредиентов рецепта
     */
    @Nullable
    public static ItemStack getFirstFoodStack(GTRecipe recipe) {
        var inputContents = recipe.getInputContents(ItemRecipeCapability.CAP);
        
        for (var content : inputContents) {
            Object contentObj = content.getContent();
            
            if (contentObj instanceof ItemStack stack) {
                if (FoodCapability.has(stack)) {
                    return stack;
                }
            } else if (contentObj instanceof Ingredient ingredient) {
                ItemStack[] items = ingredient.getItems();
                for (ItemStack item : items) {
                    if (FoodCapability.has(item)) {
                        return item;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Копирует данные о еде из источника в целевой ItemStack
     */
    public static void copyFoodData(IFood sourceFood, ItemStack targetStack) {
        IFood targetFood = FoodCapability.get(targetStack);
        if (targetFood != null && sourceFood != null) {
            // Копируем дату создания
            targetFood.setCreationDate(sourceFood.getCreationDate());
            
            // Копируем черты еды
            targetFood.getTraits().clear();
            targetFood.getTraits().addAll(sourceFood.getTraits());
        }
    }
    
    /**
     * Копирует данные о еде из исходного ItemStack в целевой
     */
    public static void copyFoodData(ItemStack sourceStack, ItemStack targetStack) {
        IFood sourceFood = FoodCapability.get(sourceStack);
        if (sourceFood != null) {
            copyFoodData(sourceFood, targetStack);
        }
    }
    
    /**
     * Проверяет, является ли предмет едой TFC
     */
    public static boolean isTFCFood(ItemStack stack) {
        return FoodCapability.has(stack);
    }
    
    /**
     * Проверяет, содержит ли рецепт хотя бы один предмет с данными о еде TFC
     */
    public static boolean hasTFCFood(GTRecipe recipe) {
        return getFirstFood(recipe) != null;
    }
    
    /**
     * Применяет данные о еде ко всем выходным предметам рецепта, которые являются едой TFC
     */
    public static void applyFoodDataToOutputs(GTRecipe recipe, IFood sourceFood) {
        var outputContents = recipe.getOutputContents(ItemRecipeCapability.CAP);
        
        for (var content : outputContents) {
            Object contentObj = content.getContent();
            
            if (contentObj instanceof ItemStack stack) {
                if (isTFCFood(stack)) {
                    copyFoodData(sourceFood, stack);
                }
            } else if (contentObj instanceof Ingredient ingredient) {
                ItemStack[] items = ingredient.getItems();
                for (ItemStack item : items) {
                    if (isTFCFood(item)) {
                        copyFoodData(sourceFood, item);
                    }
                }
            }
        }
    }
    
    /**
     * Применяет данные о еде ко всем выходным предметам рецепта на основе первого найденного входного предмета с едой
     */
    public static void applyFoodDataToOutputs(GTRecipe recipe) {
        IFood sourceFood = getFirstFood(recipe);
        if (sourceFood != null) {
            applyFoodDataToOutputs(recipe, sourceFood);
        }
    }

    /**
     * Проверяет, содержат ли входные данные машины гнилую еду
     * Проверяет реальные предметы в слотах машины
     * ИЗМЕНЕНО: Теперь используется только для логирования, рецепты не блокируются
     */
    public static boolean hasRottenFoodInMachine(MetaMachine machine) {
        if (machine == null) {
            return false;
        }
        
        try {
            su.terrafirmagreg.core.TFGCore.LOGGER.debug("Проверяем машину {} на гнилую еду в слотах", 
                machine.getDefinition().getId());
            
            // Проверяем основную машину
            if (checkMachineInventory(machine)) {
                return true;
            }
            
            // Если это мультиблочная машина-контроллер, проверяем её части
            if (machine instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController multiController) {
                su.terrafirmagreg.core.TFGCore.LOGGER.debug("Проверяем мультиблочную машину {} с {} частями", 
                    machine.getDefinition().getId(), multiController.getParts().size());
                
                for (var part : multiController.getParts()) {
                    if (part instanceof MetaMachine partMachine) {
                        su.terrafirmagreg.core.TFGCore.LOGGER.debug("Проверяем часть мультиблока: {}", 
                            partMachine.getDefinition().getId());
                        
                        if (checkMachineInventory(partMachine)) {
                            return true;
                        }
                    }
                }
            }
            
            su.terrafirmagreg.core.TFGCore.LOGGER.debug("Гнилая еда в машине {} не найдена", 
                machine.getDefinition().getId());
            return false;
            
        } catch (Exception e) {
            su.terrafirmagreg.core.TFGCore.LOGGER.error("Ошибка при проверке гнилой еды в машине {}: {}", 
                machine.getDefinition().getId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Проверяет инвентарь конкретной машины на наличие гнилой еды
     */
    private static boolean checkMachineInventory(MetaMachine machine) {
        // Получаем все трейты машины
        for (var trait : machine.getTraits()) {
            // Ищем трейты, которые являются обработчиками предметов
            if (trait instanceof com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler itemHandler) {
                // Проверяем только входные слоты
                if (itemHandler.getHandlerIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.IN || 
                    itemHandler.getHandlerIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.BOTH) {
                    
                    su.terrafirmagreg.core.TFGCore.LOGGER.debug("Проверяем входной инвентарь с {} слотами", 
                        itemHandler.getSlots());
                    
                    // Проверяем каждый слот
                    for (int i = 0; i < itemHandler.getSlots(); i++) {
                        ItemStack stack = itemHandler.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            IFood food = FoodCapability.get(stack);
                            if (food != null) {
                                boolean isRotten = food.isRotten();
                                su.terrafirmagreg.core.TFGCore.LOGGER.debug("Найдена еда в слоте {}: {} (гнилая: {})", 
                                    i, stack.getDisplayName().getString(), isRotten);
                                
                                if (isRotten) {
                                    su.terrafirmagreg.core.TFGCore.LOGGER.info("НАЙДЕНА ГНИЛАЯ ЕДА в машине {}, слот {}: {}", 
                                        machine.getDefinition().getId(), i, stack.getDisplayName().getString());
                                    return true;
                                }
                            } else {
                                su.terrafirmagreg.core.TFGCore.LOGGER.debug("Предмет в слоте {} не является едой TFC: {}", 
                                    i, stack.getDisplayName().getString());
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
                        su.terrafirmagreg.core.TFGCore.LOGGER.debug("Проверяем прокси-инвентарь с {} слотами", 
                            proxyTrait.getSlots());
                        
                        for (int i = 0; i < proxyTrait.getSlots(); i++) {
                            ItemStack stack = proxyTrait.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                IFood food = FoodCapability.get(stack);
                                if (food != null) {
                                    boolean isRotten = food.isRotten();
                                    su.terrafirmagreg.core.TFGCore.LOGGER.debug("Найдена еда в прокси-слоте {}: {} (гнилая: {})", 
                                        i, stack.getDisplayName().getString(), isRotten);
                                    
                                    if (isRotten) {
                                        su.terrafirmagreg.core.TFGCore.LOGGER.info("НАЙДЕНА ГНИЛАЯ ЕДА в прокси-инвентаре машины {}, слот {}: {}", 
                                            machine.getDefinition().getId(), i, stack.getDisplayName().getString());
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
} 