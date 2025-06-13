package su.terrafirmagreg.core.common.integration;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import su.terrafirmagreg.core.TFGCore;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Класс интеграции между TerraFirmaCraft и GregTech Modern
 * Обрабатывает события рецептов для правильной работы с системой порчи еды
 */
@Mod.EventBusSubscriber(modid = TFGCore.MOD_ID)
public class TFCGTIntegration {

    /**
     * Кэш для сохранения данных о еде между началом и завершением рецепта
     * Ключ: уникальный идентификатор машины, Значение: данные о еде
     */
    private static final Map<String, net.dries007.tfc.common.capabilities.food.IFood> savedFoodData = new ConcurrentHashMap<>();

    /**
     * Сохраняет данные о еде из входных предметов ДО выполнения рецепта
     * Это позволяет захватить состояние входных предметов до их потребления
     */
    public static void saveFoodDataBeforeRecipe(GTRecipe recipe, MetaMachine machine) {
        if (recipe == null || machine == null) {
            return;
        }

        try {
            // Проверяем, содержит ли рецепт еду TFC
            if (!TFCFoodHelper.hasTFCFood(recipe)) {
                return;
            }

            // Получаем данные о еде из входных предметов машины ДО их потребления
            net.dries007.tfc.common.capabilities.food.IFood sourceFood = TFCFoodModifier.getFoodFromMachineSlots(machine);
            
            if (sourceFood != null) {
                // Создаем уникальный ключ для машины
                String machineKey = getMachineKey(machine);
                
                // Сохраняем данные о еде
                savedFoodData.put(machineKey, sourceFood);
                
                TFGCore.LOGGER.info("СОХРАНЕНЫ данные о еде ДО выполнения рецепта {}: дата создания = {}, гнилая = {}", 
                    recipe.id, sourceFood.getCreationDate(), sourceFood.isRotten());
            } else {
                TFGCore.LOGGER.debug("Не найдены данные о еде для сохранения в рецепте {}", recipe.id);
            }
            
        } catch (Exception e) {
            TFGCore.LOGGER.error("Ошибка при сохранении данных о еде для рецепта {} в машине {}: {}", 
                recipe.id, machine.getDefinition().getId(), e.getMessage());
        }
    }

    /**
     * Создает уникальный ключ для машины
     */
    private static String getMachineKey(MetaMachine machine) {
        return machine.getDefinition().getId().toString() + "_" + machine.getPos().toString();
    }

    /**
     * Проверяет, следует ли заблокировать рецепт из-за гнилой еды во входных данных машины
     * ИЗМЕНЕНО: Теперь рецепты с гнилой едой НЕ блокируются, а обрабатываются с копированием данных о еде
     */
    public static boolean shouldBlockRottenFood(GTRecipe recipe, MetaMachine machine) {
        if (recipe == null || machine == null) {
            return false;
        }

        try {
            // Проверяем, разрешена ли гнилая еда для этой машины (для специальных машин типа компостеров)
            if (shouldAllowRottenFood(machine)) {
                TFGCore.LOGGER.debug("Машина {} разрешает гнилую еду, пропускаем проверку", 
                    machine.getDefinition().getId());
                return false;
            }

            // НОВАЯ ЛОГИКА: Больше не блокируем рецепты с гнилой едой
            // Вместо этого данные о еде будут скопированы в processRecipe()
            boolean hasRottenInRecipe = TFCFoodHelper.hasRottenFood(recipe);
            boolean hasRottenInMachine = TFCFoodHelper.hasRottenFoodInMachine(machine);
            
            if (hasRottenInRecipe || hasRottenInMachine) {
                TFGCore.LOGGER.info("ОБНАРУЖЕНА ГНИЛАЯ ЕДА в рецепте {} для машины {} (в рецепте: {}, в машине: {}). Рецепт будет выполнен с копированием данных о еде.", 
                    recipe.id, machine.getDefinition().getId(), hasRottenInRecipe, hasRottenInMachine);
            }
            
            // Больше не блокируем рецепты - всегда возвращаем false
            return false;
        } catch (Exception e) {
            TFGCore.LOGGER.error("Ошибка при проверке гнилой еды для рецепта {} в машине {}: {}", 
                recipe.id, machine.getDefinition().getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Обрабатывает рецепт, применяя модификации данных о еде TFC
     * ИЗМЕНЕНО: Теперь всегда копирует данные о еде из входных ингредиентов в выходные продукты
     */
    public static GTRecipe processRecipe(GTRecipe recipe, MetaMachine machine) {
        if (recipe == null || machine == null) {
            return recipe;
        }

        try {
            // Проверяем, содержит ли рецепт еду TFC
            if (!TFCFoodHelper.hasTFCFood(recipe)) {
                TFGCore.LOGGER.debug("Рецепт {} не содержит еду TFC, пропускаем обработку", recipe.id);
                return recipe;
            }

            TFGCore.LOGGER.debug("Обрабатываем рецепт {} с едой TFC для машины {}", 
                recipe.id, machine.getDefinition().getId());

            // Применяем модификатор еды - теперь всегда копируем данные о еде
            GTRecipe processedRecipe = TFCFoodModifier.apply(recipe, machine);
            
            if (processedRecipe != recipe) {
                TFGCore.LOGGER.info("Применено копирование данных о еде для рецепта {} в машине {}", 
                    recipe.id, machine.getDefinition().getId());
            }
            
            return processedRecipe;
        } catch (Exception e) {
            TFGCore.LOGGER.error("Ошибка при обработке рецепта {} для машины {}: {}", 
                recipe.id, machine.getDefinition().getId(), e.getMessage());
            return recipe;
        }
    }

    /**
     * Проверяет, разрешена ли гнилая еда для данной машины
     * Некоторые машины (например, компостеры) могут использовать гнилую еду без копирования данных
     */
    private static boolean shouldAllowRottenFood(MetaMachine machine) {
        if (machine == null) {
            return false;
        }

        String machineId = machine.getDefinition().getId().toString();
        
        // Список машин, которые могут использовать гнилую еду без ограничений
        return machineId.contains("composter") || 
               machineId.contains("fermenter") ||
               machineId.contains("biogas");
    }

    /**
     * Применяет данные о еде к реальным выходным предметам в машине
     * Этот метод вызывается после того, как рецепт завершен и предметы созданы
     * ИЗМЕНЕНО: Теперь использует сохраненные данные о еде вместо поиска в пустых слотах
     */
    public static void applyFoodDataToMachineOutputs(GTRecipe recipe, MetaMachine machine) {
        if (recipe == null || machine == null) {
            return;
        }

        try {
            // Создаем ключ для поиска сохраненных данных
            String machineKey = getMachineKey(machine);
            
            // Получаем сохраненные данные о еде
            net.dries007.tfc.common.capabilities.food.IFood sourceFood = savedFoodData.get(machineKey);
            
            if (sourceFood == null) {
                // Если нет сохраненных данных, пытаемся найти в рецепте (fallback)
                sourceFood = TFCFoodHelper.getFirstFood(recipe);
                TFGCore.LOGGER.debug("Используем данные о еде из рецепта (fallback)");
            } else {
                TFGCore.LOGGER.debug("Используем СОХРАНЕННЫЕ данные о еде");
                // Удаляем использованные данные из кэша
                savedFoodData.remove(machineKey);
            }
            
            if (sourceFood == null) {
                TFGCore.LOGGER.debug("Не найдены данные о еде для применения к выходным предметам");
                return;
            }

            TFGCore.LOGGER.info("Применяем данные о еде к выходным предметам: дата создания = {}, гнилая = {}", 
                sourceFood.getCreationDate(), sourceFood.isRotten());

            // Применяем данные о еде ко всем выходным слотам машины
            applyFoodDataToOutputSlots(machine, sourceFood);
            
        } catch (Exception e) {
            TFGCore.LOGGER.error("Ошибка при применении данных о еде к выходным предметам машины {}: {}", 
                machine.getDefinition().getId(), e.getMessage());
        }
    }

    /**
     * Применяет данные о еде ко всем выходным слотам машины
     */
    private static void applyFoodDataToOutputSlots(MetaMachine machine, net.dries007.tfc.common.capabilities.food.IFood sourceFood) {
        try {
            // Получаем все трейты машины
            for (var trait : machine.getTraits()) {
                // Ищем трейты, которые являются обработчиками предметов
                if (trait instanceof com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler itemHandler) {
                    // Проверяем только выходные слоты
                    if (itemHandler.getHandlerIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.OUT || 
                        itemHandler.getHandlerIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.BOTH) {
                        
                        // Проверяем каждый слот
                        for (int i = 0; i < itemHandler.getSlots(); i++) {
                            net.minecraft.world.item.ItemStack stack = itemHandler.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                net.dries007.tfc.common.capabilities.food.IFood outputFood = 
                                    net.dries007.tfc.common.capabilities.food.FoodCapability.get(stack);
                                if (outputFood != null) {
                                    // Копируем данные о еде
                                    outputFood.setCreationDate(sourceFood.getCreationDate());
                                    outputFood.getTraits().clear();
                                    outputFood.getTraits().addAll(sourceFood.getTraits());
                                    
                                    TFGCore.LOGGER.info("Применены данные о еде к выходному предмету в слоте {}: {} (гнилая: {})", 
                                        i, stack.getDisplayName().getString(), outputFood.isRotten());
                                }
                            }
                        }
                    }
                }
                // Также проверяем прокси-трейты
                else if (trait instanceof com.gregtechceu.gtceu.api.machine.trait.ItemHandlerProxyTrait proxyTrait) {
                    if (proxyTrait.getCapabilityIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.OUT || 
                        proxyTrait.getCapabilityIO() == com.gregtechceu.gtceu.api.capability.recipe.IO.BOTH) {
                        
                        if (proxyTrait.getProxy() != null) {
                            for (int i = 0; i < proxyTrait.getSlots(); i++) {
                                net.minecraft.world.item.ItemStack stack = proxyTrait.getStackInSlot(i);
                                if (!stack.isEmpty()) {
                                    net.dries007.tfc.common.capabilities.food.IFood outputFood = 
                                        net.dries007.tfc.common.capabilities.food.FoodCapability.get(stack);
                                    if (outputFood != null) {
                                        // Копируем данные о еде
                                        outputFood.setCreationDate(sourceFood.getCreationDate());
                                        outputFood.getTraits().clear();
                                        outputFood.getTraits().addAll(sourceFood.getTraits());
                                        
                                        TFGCore.LOGGER.info("Применены данные о еде к выходному предмету в прокси-слоте {}: {} (гнилая: {})", 
                                            i, stack.getDisplayName().getString(), outputFood.isRotten());
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
                        applyFoodDataToOutputSlots(partMachine, sourceFood);
                    }
                }
            }
        } catch (Exception e) {
            TFGCore.LOGGER.error("Ошибка при применении данных о еде к выходным слотам машины: {}", e.getMessage());
        }
    }
} 