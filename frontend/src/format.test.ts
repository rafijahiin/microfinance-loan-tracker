import { describe, expect, it } from 'vitest'
import { taka, percent, day } from './format'

describe('taka', () => {
  it('always shows two decimal places', () => {
    // Money with a ragged number of decimals in a column is unreadable, and
    // 1000 and 1000.00 in adjacent rows look like different kinds of figure.
    expect(taka('1000')).toBe('1,000.00')
    expect(taka('1000.5')).toBe('1,000.50')
    expect(taka(0)).toBe('0.00')
  })

  it('groups digits the way amounts are read here', () => {
    // en-IN grouping: 1,88,886.01 rather than 188,886.01.
    expect(taka('188886.01')).toBe('1,88,886.01')
  })

  it('accepts the decimal strings the API actually sends', () => {
    // Amounts arrive as strings on purpose, so that they never pass through a
    // binary float on the way to the screen.
    expect(taka('32769.23')).toBe('32,769.23')
  })

  it('returns the input unchanged rather than NaN when it is not a number', () => {
    expect(taka('not-a-number')).toBe('not-a-number')
  })
})

describe('percent', () => {
  it('renders a fraction as a percentage', () => {
    expect(percent('0.2812')).toBe('28.1%')
    expect(percent(0)).toBe('0.0%')
    expect(percent('1')).toBe('100.0%')
  })

  it('honours the requested precision', () => {
    expect(percent('0.2812', 2)).toBe('28.12%')
  })
})

describe('day', () => {
  it('renders an ISO date the way it is written locally', () => {
    expect(day('2026-02-28')).toBe('28/02/2026')
  })

  it('shows a dash rather than the word null for a missing date', () => {
    expect(day(null)).toBe('\u2014')
  })

  it('leaves an unexpected format alone instead of mangling it', () => {
    expect(day('28 February')).toBe('28 February')
  })
})
