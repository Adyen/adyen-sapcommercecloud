package com.adyen.backoffice.dto;

import java.util.List;

public class WebhookResponseWsDTO {

    private List<WebhookDataWsDTO> data;
    private LinksWsDTO _links;

    public WebhookResponseWsDTO() {
    }

    public List<WebhookDataWsDTO> getData() {
        return data;
    }

    public void setData(List<WebhookDataWsDTO> data) {
        this.data = data;
    }

    public LinksWsDTO get_links() {
        return _links;
    }

    public void set_links(LinksWsDTO _links) {
        this._links = _links;
    }
}