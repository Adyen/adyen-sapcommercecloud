
const guidRegexp = /^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/

export function isGuid(string: string): boolean {
    return guidRegexp.test(string)
}