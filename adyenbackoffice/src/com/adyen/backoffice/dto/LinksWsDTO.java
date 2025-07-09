package com.adyen.backoffice.dto;

import java.util.List;

public class LinksWsDTO {
    
    private LinkWsDTO self;
    private LinkWsDTO next;
    private LinkWsDTO prev;
    private LinkWsDTO first;
    private LinkWsDTO last;
    
    public LinkWsDTO getSelf() {
        return self;
    }
    
    public void setSelf(LinkWsDTO self) {
        this.self = self;
    }
    
    public LinkWsDTO getNext() {
        return next;
    }
    
    public void setNext(LinkWsDTO next) {
        this.next = next;
    }
    
    public LinkWsDTO getPrev() {
        return prev;
    }
    
    public void setPrev(LinkWsDTO prev) {
        this.prev = prev;
    }
    
    public LinkWsDTO getFirst() {
        return first;
    }
    
    public void setFirst(LinkWsDTO first) {
        this.first = first;
    }
    
    public LinkWsDTO getLast() {
        return last;
    }
    
    public void setLast(LinkWsDTO last) {
        this.last = last;
    }
}
