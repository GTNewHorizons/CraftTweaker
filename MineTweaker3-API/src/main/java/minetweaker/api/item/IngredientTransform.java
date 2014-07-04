/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package minetweaker.api.item;

import stanhebben.zenscript.annotations.ZenExpansion;
import stanhebben.zenscript.annotations.ZenMethod;

/**
 *
 * @author Stan
 */
@ZenExpansion("minetweaker.item.IIngredient")
public class IngredientTransform {
	/**
	 * Makes the item reusable. Prevents consumption of the item upon crafting.
	 * 
	 * @param ingredient target value
	 * @return reuse transformer
	 */
	@ZenMethod
	public static IIngredient reuse(IIngredient ingredient) {
		return ingredient.transform(new IItemTransformer() {
			@Override
			public IItemStack transform(IItemStack item) {
				return item.withAmount(item.getAmount() + 1);
			}
		});
	}
	
	/**
	 * Damages the item. Also makes the item reusable. Will damage the item
	 * for 1 point upon crafting and consume it when broken.
	 * 
	 * @param ingredient target value
	 * @return damage transformer
	 */
	@ZenMethod
	public static IIngredient transformDamage(IIngredient ingredient) {
		return ingredient.transform(new IItemTransformer() {
			@Override
			public IItemStack transform(IItemStack item) {
				int newDamage = item.getDamage() + 1;
				if (newDamage >= item.getMaxDamage()) {
					return item.withAmount(item.getAmount()).withDamage(0);
				} else {
					return item.withAmount(item.getAmount() + 1).withDamage(newDamage);
				}
			}
		});
	}
	
	/**
	 * Damages the item for a specific amount. Also makes the item reusable.
	 * Upon reaching maximum damage, the item will be consumed. Take care to
	 * set the proper condition such that an almost-broken item becomes
	 * invalid for crafting.
	 * 
	 * @param ingredient target value
	 * @param damage damage to be applied
	 * @return damage transformer
	 */
	@ZenMethod
	public static IIngredient transformDamage(IIngredient ingredient, final int damage) {
		return ingredient.transform(new IItemTransformer() {
			@Override
			public IItemStack transform(IItemStack item) {
				int newDamage = item.getDamage() + damage;
				if (newDamage >= item.getMaxDamage()) {
					return item.withAmount(item.getAmount() - 1).withDamage(newDamage - item.getMaxDamage());
				} else {
					return item.withDamage(newDamage);
				}
			}
		});
	}
	
	/**
	 * Causes the item to be replaced upon crafting. Can be used, for instance,
	 * to return empty bottles or buckets.
	 * 
	 * @param ingredient target value
	 * @param withItem replacement item
	 * @return replacement transformer
	 */
	@ZenMethod
	public static IIngredient transformReplace(IIngredient ingredient, final IItemStack withItem) {
		return ingredient.transform(new IItemTransformer() {
			@Override
			public IItemStack transform(IItemStack item) {
				return withItem.withAmount(withItem.getAmount() + 1);
			}
		});
	}
	
	/**
	 * Causes multiple items to be consumed. Take care to set a condition for
	 * a minimum stack size too, as otherwise smaller stacks would still be
	 * accepted for input.
	 * 
	 * @param ingredient target value
	 * @param amount consumption amount
	 * @return consuming transformer
	 */
	@ZenMethod
	public static IIngredient transformConsume(IIngredient ingredient, final int amount) {
		return ingredient.transform(new IItemTransformer() {
			@Override
			public IItemStack transform(IItemStack item) {
				return item.withAmount(Math.max(item.getAmount() - amount, 0) + 1);
			}
		});
	}
}