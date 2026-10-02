import { describe, expect, it } from 'vitest';
import { parseSupportAmount } from '@/modules/finance/api/financeTypes';

describe('support amounts', () => {
    it.each(['', '0', '-1', '0.99', '1000.01', 'NaN', 'Infinity', '1e2', '1.001', '1,00'])('rejects %s without silently changing the amount', value => {
        expect(parseSupportAmount(value)).toBeNull();
    });
    it.each([['1', 100], ['1.01', 101], ['12.3', 1230], ['1000.00', 100000], [' 5.25 ', 525]])('parses %s exactly', (value, cents) => {
        expect(parseSupportAmount(String(value))).toBe(cents);
    });
});
