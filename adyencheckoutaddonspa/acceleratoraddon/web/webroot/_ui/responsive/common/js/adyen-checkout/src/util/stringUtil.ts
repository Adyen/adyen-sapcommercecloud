export function isNotEmpty(s: string | null): boolean {
    return s != null && s.length > 0;
}

export function isEmpty(s: string): boolean {
    return !isNotEmpty(s)
}

export function formatStringWithPlaceholders(stringWithPlaceholders: string, ...args: any[]): string {
    return stringWithPlaceholders.replace(/{(\d+)}/g, (placeholderWithDelimiters: string, placeholderWithoutDelimiters: number) => {
        if (placeholderWithoutDelimiters >= 0 && placeholderWithoutDelimiters < args.length) {
            return args[placeholderWithoutDelimiters];
        }
        return placeholderWithDelimiters;
    })
}