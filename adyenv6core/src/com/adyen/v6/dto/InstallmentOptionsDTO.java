package com.adyen.v6.dto;

import java.util.List;

public class InstallmentOptionsDTO {
    private CardInstallmentOptions card;
    private ShowInstallmentAmounts showInstallmentAmounts;

    public CardInstallmentOptions getCard() {
        return card;
    }

    public void setCard(CardInstallmentOptions card) {
        this.card = card;
    }

    public ShowInstallmentAmounts getShowInstallmentAmounts() {
        return showInstallmentAmounts;
    }

    public void setShowInstallmentAmounts(ShowInstallmentAmounts showInstallmentAmounts) {
        this.showInstallmentAmounts = showInstallmentAmounts;
    }

    public static class CardInstallmentOptions {
        private List<Integer> values;
        private List<String> plans;

        public List<Integer> getValues() {
            return values;
        }

        public void setValues(List<Integer> values) {
            this.values = values;
        }

        public List<String> getPlans() {
            return plans;
        }

        public void setPlans(List<String> plans) {
            this.plans = plans;
        }
    }

    public static class ShowInstallmentAmounts {
        private List<Integer> values;
        private List<String> plans;

        public List<Integer> getValues() {
            return values;
        }

        public void setValues(List<Integer> values) {
            this.values = values;
        }

        public List<String> getPlans() {
            return plans;
        }

        public void setPlans(List<String> plans) {
            this.plans = plans;
        }
    }
}