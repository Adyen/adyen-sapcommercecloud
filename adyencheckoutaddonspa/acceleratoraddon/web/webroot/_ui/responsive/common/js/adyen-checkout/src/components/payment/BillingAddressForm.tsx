import React from "react";
import { AddressModel } from "../../reducers/types";
import { InputCheckbox } from "../controls/InputCheckbox";
import { translationsStore } from "../../store/translationsStore";

interface BillingAddressFormProps {
    useDifferentBillingAddress: boolean;
    saveInAddressBook: boolean;
    billingAddress: AddressModel;
    errorFieldCodes: string[];
    onUseDifferentBillingAddressChange: (value: boolean) => void;
    onSaveInAddressBookChange: (value: boolean) => void;
    onCountryCodeChange: (countryCode: string) => void;
    onRegionCodeChange: (regionCode: string) => void;
    onTitleCodeChange: (titleCode: string) => void;
    onFirstNameChange: (firstName: string) => void;
    onLastNameChange: (lastName: string) => void;
    onLine1Change: (line1: string) => void;
    onLine2Change: (line2: string) => void;
    onCityChange: (city: string) => void;
    onPostCodeChange: (postCode: string) => void;
    onPhoneNumberChange: (phoneNumber: string) => void;
    onCompanyNameChange: (companyName: string) => void;
    onRegistrationNumberChange: (registrationNumber: string) => void;
    onTaxNumberChange: (taxNumber: string) => void;
    onSelectAddress: (address: AddressModel) => void;
}

export const BillingAddressForm: React.FC<BillingAddressFormProps> = ({
    useDifferentBillingAddress,
    onUseDifferentBillingAddressChange
}) => {
    return (
        <>
            {/* @ts-ignore */}
            <InputCheckbox
                fieldName={translationsStore.get("checkout.multi.payment.useSameAddressAsShippingAddress")}
                onChange={(value) => onUseDifferentBillingAddressChange(!value)}
                checked={!useDifferentBillingAddress}
            />
        </>
    );
};