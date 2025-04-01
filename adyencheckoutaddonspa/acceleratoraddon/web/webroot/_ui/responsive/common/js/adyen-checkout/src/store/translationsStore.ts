import {isNotEmpty} from "../util/stringUtil";

export type TranslationData = { [key: string]: string };

export let translationsStore: TranslationStore;

export function setTranslationStore(store: TranslationStore) {
    translationsStore = store;
}

export class TranslationStore {
    private readonly translationData: TranslationData;

    constructor(translationData: TranslationData) {
        this.translationData = translationData;
    }

    public get(key: string) {
        let translation = this.translationData[key];

        if (isNotEmpty(translation)) {
            return translation
        }

        console.warn(`No translation for key ${key}`)
        return ""
    }
}