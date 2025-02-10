package com.adyen.v6.populator;

import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;

public class AdyenAddressPopulator implements Populator<AddressModel, AddressData> {

    @Override
    public void populate(AddressModel addressModel, AddressData addressData) throws ConversionException {
        addressData.setCompanyName(addressModel.getCompany());
        addressData.setTaxNumber(addressModel.getTaxNumber());
        addressData.setRegistrationNumber(addressModel.getRegistrationNumber());
    }
}
