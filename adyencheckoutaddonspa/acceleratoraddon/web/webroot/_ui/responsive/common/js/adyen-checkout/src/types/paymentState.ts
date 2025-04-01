export interface CardState {
    isValid: boolean,
    data: {
        paymentMethod: {
            type: string,
            encryptedCardNumber?: string,
            encryptedExpiryMonth?: string,
            encryptedExpiryYear?: string,
            encryptedSecurityCode: string,
            storedPaymentMethodId?: string,
            holderName: string,
            brand: string
        },
        browserInfo: any,
        storePaymentMethod?: boolean
    }
}