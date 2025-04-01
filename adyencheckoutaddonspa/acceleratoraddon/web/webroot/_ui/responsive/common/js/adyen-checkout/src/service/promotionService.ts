import {CartData, OrderEntryData, PromotionOrderEntryConsumedData, PromotionResultData} from "../types/cartData";
import {isNotEmpty} from "../util/stringUtil";

export class PromotionService {

    public static isConsumedByEntry(consumed: PromotionOrderEntryConsumedData, entry: OrderEntryData): boolean {
        let orderEntryNumber = consumed.orderEntryNumber;
        if (entry.entries == null || entry.entries.length === 0) {
            return orderEntryNumber === entry.entryNumber
        }

        let result: boolean = false
        for (let e of entry.entries) {
            result = result || (e.entryNumber === orderEntryNumber)
        }
        return result
    }

    public static doesAppliedPromotionExistForOrderEntryOrOrderEntryGroup(cart: CartData, entry: OrderEntryData): boolean {
        if (entry.entries == null || entry.entries.length === 0) {
            return this.doesAppliedPromotionExistForOrderEntry(cart, entry.entryNumber)
        }

        let result: boolean = false
        for (let e of entry.entries) {
            result = result || this.doesAppliedPromotionExistForOrderEntry(cart, e.entryNumber)
        }
        return result;
    }

    private static doesAppliedPromotionExistForOrderEntry(cart: CartData, entryNumber: number): boolean {
        return cart != null && this.doesPromotionExistForOrderEntry(cart.appliedProductPromotions, entryNumber);
    }

    private static doesPromotionExistForOrderEntry(productPromotions: PromotionResultData[], entryNumber: number): boolean {
        if (productPromotions != null && productPromotions.length > 0) {
            for (let productPromotion of productPromotions) {
                if (isNotEmpty(productPromotion.description) && this.doesPromotionExistForOrderEntryNumber(entryNumber, productPromotion)) {
                    return true
                }
            }
        }
        return false
    }

    private static doesPromotionExistForOrderEntryNumber(entryNumberToFind: number, productPromotion: PromotionResultData): boolean {
        let consumedEntries = productPromotion.consumedEntries;

        if (consumedEntries != null && consumedEntries.length > 0) {
            for (let consumedEntry of consumedEntries) {
                if (entryNumberToFind === consumedEntry.orderEntryNumber) {
                    return true
                }
            }
        }
        return false;
    }
}