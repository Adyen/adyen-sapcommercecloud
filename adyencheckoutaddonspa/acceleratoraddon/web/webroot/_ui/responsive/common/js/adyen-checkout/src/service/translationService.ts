import axios, {AxiosResponse} from "axios";
import {CSRFToken, urlContextPath} from "../util/baseUrlUtil";
import {translationKeys} from "../const/translationConst";
import {setTranslationStore, TranslationData, TranslationStore} from "../store/translationsStore";

export class TranslationService {
    static fetchTranslations() {
        return axios.post(urlContextPath + '/api/checkout/translations', translationKeys, {
            headers: {
                'Content-Type': 'application/json',
                'CSRFToken': CSRFToken
            }
        })
            .then((response: AxiosResponse<TranslationData>) => {
                setTranslationStore(new TranslationStore(response.data));
                return true;
            })
            .catch(() => {
                console.error('Error on fetching translations')
                return false;
            })
    }
}