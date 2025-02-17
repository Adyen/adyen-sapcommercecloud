package com.adyen.v6.populator;

import de.hybris.platform.converters.Populator;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;
import de.hybris.platform.ticket.model.CsTicketModel;
import de.hybris.platform.ticketsystem.data.CsTicketParameter;

public class AdyenCsTicketParameterPopulator implements Populator<CsTicketParameter, CsTicketModel> {
    @Override
    public void populate(CsTicketParameter csTicketParameter, CsTicketModel csTicketModel) throws ConversionException {
        csTicketModel.setPspReference(csTicketParameter.getPspReference());
        csTicketModel.setOriginalReference(csTicketParameter.getOriginalReference());
        csTicketModel.setAmount(csTicketParameter.getAmount());
        csTicketModel.setCurrency(csTicketParameter.getCurrency());
    }
}
