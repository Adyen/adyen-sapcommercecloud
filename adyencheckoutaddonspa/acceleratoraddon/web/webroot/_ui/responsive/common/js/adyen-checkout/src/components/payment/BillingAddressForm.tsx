import React from "react";
import { AddressModel } from "../../reducers/types";
import { InputCheckbox } from "../controls/InputCheckbox";
import { translationsStore } from "../../store/translationsStore";
import AddressSection from "../common/AddressSection";
import { ScrollHere } from "../common/ScrollTo";

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
    saveInAddressBook,
    billingAddress,
    errorFieldCodes,
    onUseDifferentBillingAddressChange,
    onSaveInAddressBookChange,
    onCountryCodeChange,
    onRegionCodeChange,
    onTitleCodeChange,
    onFirstNameChange,
    onLastNameChange,
    onLine1Change,
    onLine2Change,
    onCityChange,
    onPostCodeChange,
    onPhoneNumberChange,
    onCompanyNameChange,
    onRegistrationNumberChange,
    onTaxNumberChange,
    onSelectAddress
}) => {
    const renderScrollOnErrorCodes = (): React.JSX.Element => {
        if (errorFieldCodes && errorFieldCodes.length > 0) {
            // @ts-ignore
            return <ScrollHere />;
        }
        return <></>;
    };

    const renderBillingAddressForm = (): React.JSX.Element => {
        if (useDifferentBillingAddress) {
            return (
                <>
                    <hr />
                    {renderScrollOnErrorCodes()}
                    <div className="headline">Billing Address</div>
                    <AddressSection 
                        address={billingAddress}
                        saveInAddressBook={saveInAddressBook}
                        errorFieldCodes={errorFieldCodes}
                        errorFieldCodePrefix="billingAddress."
                        onCountryCodeChange={onCountryCodeChange}
                        onRegionCodeChange={onRegionCodeChange}
                        onTitleCodeChange={onTitleCodeChange}
                        onFirstNameChange={onFirstNameChange}
                        onLastNameChange={onLastNameChange}
                        onLine1Change={onLine1Change}
                        onLine2Change={onLine2Change}
                        onCityChange={onCityChange}
                        onPostCodeChange={onPostCodeChange}
                        onPhoneNumberChange={onPhoneNumberChange}
                        onChangeSaveInAddressBook={onSaveInAddressBookChange}
                        onSelectAddress={onSelectAddress}
                        onCompanyNameChange={onCompanyNameChange}
                        onRegistrationNumberChange={onRegistrationNumberChange}
                        onTaxNumberChange={onTaxNumberChange}
                        isBillingAddress={true}
                    />
                    <hr />
                </>
            );
        }
        return <></>;
    };

    return (
        <>
            {/* @ts-ignore */}
            <InputCheckbox
                fieldName={translationsStore.get("checkout.multi.payment.useDifferentBillingAddress")}
                onChange={onUseDifferentBillingAddressChange}
                checked={useDifferentBillingAddress}
            />
            {renderBillingAddressForm()}
        </>
    );
};