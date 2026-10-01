import React, {useEffect, useRef, useState} from "react";
import {AdyenCheckout, Donation, DonationConfiguration} from "@adyen/adyen-web/auto";
import {adyenAxios} from "../../axios/AdyenAxios";
import {CSRFToken, urlContextPath} from "../../util/baseUrlUtil";

interface Amount {
    currency: string;
    value: number;
}

interface DonationStatePayload {
    data: {
        amount: Amount;
    };
}

interface DonationCampaign {
    id: string;
    campaignName: string;
    donation: {
        type: "roundup" | "fixedAmounts";
        currency: string;
        maxRoundupAmount?: number;
        values?: number[];
    };
    logoUrl?: string;
    nonprofitDescription?: string;
    nonprofitName?: string;
    causeName?: string;
    nonprofitUrl?: string;
    bannerUrl?: string;
    termsAndConditionsUrl?: string;
}

interface DonationContext {
    clientKey: string;
    environment: "test" | "live" | "live-us" | "live-au" | "live-apse" | "live-in";
    locale?: string;
    commercialTxAmount: number;
    currency: string;
    countryCode: string;
    campaigns: DonationCampaign[];
}

interface DonationResult {
    status?: string;
}

type DisplayState = "loading" | "error" | "hidden";

export const DonationSection: React.FC = () => {
    const mountNode = useRef<HTMLDivElement>(null);
    const donationElement = useRef<Donation | null>(null);
    const [displayState, setDisplayState] = useState<DisplayState>("loading");

    useEffect(() => {
        let cancelled = false;

        const mountDonation = async () => {
            try {
                const contextResponse = await adyenAxios.get<DonationContext>(
                    urlContextPath + "/api/checkout/donations/context",
                    {validateStatus: status => status === 200 || status === 204}
                );

                if (cancelled) {
                    return;
                }
                if (contextResponse.status === 204 || !contextResponse.data.campaigns?.length || !mountNode.current) {
                    setDisplayState("hidden");
                    return;
                }

                const context = contextResponse.data;
                const campaign = context.campaigns[0];
                const checkout = await AdyenCheckout({
                    clientKey: context.clientKey,
                    environment: context.environment,
                    locale: context.locale,
                    countryCode: context.countryCode,
                    analytics: {enabled: false}
                });
                if (cancelled || !mountNode.current) {
                    return;
                }

                const donationConfiguration = {
                    ...campaign,
                    commercialTxAmount: context.commercialTxAmount,
                    onAmountSelected: (): void => undefined,
                    onCancel: (): void => setDisplayState("hidden"),
                    onDonate: async (state: DonationStatePayload, component: Donation): Promise<void> => {
                        try {
                            component.setStatus("loading");
                            const response = await adyenAxios.post<DonationResult>(
                                urlContextPath + "/api/checkout/donations/donate",
                                {donationCampaignId: campaign.id, amount: state.data.amount as Amount},
                                {headers: {"Content-Type": "application/json", "CSRFToken": CSRFToken}}
                            );
                            if (response.data.status?.toLowerCase() === "completed") {
                                component.setStatus("success");
                            } else {
                                component.setStatus("error");
                            }
                        } catch (error) {
                            console.error("Adyen donation failed", error);
                            component.setStatus("error");
                        }
                    }
                } as unknown as DonationConfiguration;
                donationElement.current = new Donation(checkout, donationConfiguration).mount(mountNode.current);
            } catch (error) {
                console.error("Adyen donation could not be initialised", error);
                if (!cancelled) {
                    setDisplayState("error");
                }
            }
        };

        mountDonation();
        return () => {
            cancelled = true;
            donationElement.current?.unmount();
            donationElement.current = null;
        };
    }, []);

    if (displayState === "hidden") {
        return null;
    }
    return (
        <section className="adyen-donation" aria-live="polite">
            <div ref={mountNode}/>
            {displayState === "error" && <p className="alert alert-danger">Giving could not be displayed. Check the browser console for the Adyen error.</p>}
        </section>
    );
};
